from abc import abstractmethod

import torch
from torch.nn import Module

from .tree import TensorTree, stack, make_tuple

from .data import DataBag, DataPointer
from .types import TypeSpec, RecSpec


class FunctionalModule(Module):
    """
    Base class for all functional modules. Supports forward propagation and calls with raw data array.

    Also module can be instantiated - type variables substituted by actual types.

    """

    @abstractmethod
    def forward(self, data_bag):
        pass

    @abstractmethod
    def instantiate(self, **kwargs):
        pass

    def call(self, *trees, nets=None):
        if nets is None:
            nets = []
        return self(DataBag(make_tuple(trees), nets))


class ConstantLayer(FunctionalModule):
    """
    Returns constant value
    """

    def __init__(self, type_spec, position):
        super().__init__()
        self.position = position
        self.type = type_spec
        self.pointer = DataPointer.start  # Constants are defined in a global scope

    def instantiate(self, **kwargs):
        return ConstantLayer(self.type.instantiate(**kwargs), self.position)

    def forward(self, data_bag):
        if isinstance(self.type, RecSpec):
            # "Unwrap" recursive definition once
            type_spec = self.type.unwrap(1)
        else:
            type_spec = self.type
        if not isinstance(type_spec, TypeSpec):
            raise ValueError("Unexpected type of type spec")

        # Construct constant value
        rows = data_bag.size
        size = type_spec.size()
        tensor = torch.zeros(rows, size)
        tensor[:, self.position] = torch.ones(rows)
        children = [None for _ in range(size)]

        return TensorTree(tensor, type_spec, children)


class ConstructorLayer(FunctionalModule):
    """
    Constructs one data-type from another and can't be learned
    """

    def __init__(self, to_type, position):
        super().__init__()
        self.type = to_type
        self.position = position
        self.pointer = DataPointer.start  # Constructors are defined at global scope

    def instantiate(self, **kwargs):
        return ConstructorLayer(
            self.type.instantiate(**kwargs),
            self.position
        )

    def forward(self, data_bag):
        data = data_bag.data
        if isinstance(self.type, RecSpec):
            # "Unwrap" recursive definition once
            type_spec = self.type.unwrap(1)
        else:
            type_spec = self.type
        if not isinstance(type_spec, TypeSpec):
            raise ValueError("Unexpected type of type spec")

        type_operands = type_spec.operands
        type_operands_before = type_operands[:self.position]
        type_operands_after = type_operands[self.position + 1:]
        data_type = TypeSpec([*type_operands_before, data.type.operands[0], *type_operands_after])

        size = type_spec.size()
        size_before = sum(map(lambda t: t.size(), type_operands_before))
        size_after = sum(map(lambda t: t.size(), type_operands_after))
        new_tensor = torch.zeros(data_bag.size, size)
        new_tensor[:, self.position] = data.presence()
        children = [*([None] * size_before), *data.children, *([None] * size_after)]

        return TensorTree(new_tensor, data_type, children)


class VariableLayer:
    """
    Gets value of some parameter from the data bag
    """

    class Data(FunctionalModule):
        def __init__(self, position):
            super().__init__()
            self.position = position

        def instantiate(self, **kwargs):
            return self

        def forward(self, data_bag):
            return data_bag.data.children[self.position]

    class Net(FunctionalModule):
        def __init__(self, pos):
            super().__init__()
            self.pos = pos

        def instantiate(self, **kwargs):
            return self

        def forward(self, data_bag):
            return data_bag.get_net(self.pos)

    class External(FunctionalModule):
        def __init__(self, net):
            super().__init__()
            self.net = net
            self.add_module('external', net)

        def instantiate(self, **kwargs):
            return VariableLayer.External(self.net.instantiate(**kwargs))

        def forward(self, data_bag):
            return self.net


class AnonymousNetLayer(FunctionalModule):
    """
    Net that was anonymously declared. Keeps pointer on it's scope
    """

    def __init__(self, net, pointer):
        super().__init__()
        self.net = net
        self.pointer = pointer
        self.add_module('inner', net)

    def instantiate(self, **kwargs):
        return AnonymousNetLayer(
            self.net.instantiate(**kwargs),
            self.pointer,
        )

    def forward(self, *inputs):
        return self.net(*inputs)


class RecursiveLayer(FunctionalModule):
    """
    Recursive network. Provides reference on itself for recursive calls.
    """

    def __init__(self, net, pointer):
        super().__init__()
        self.net = net
        self.pointer = pointer
        self.add_module('inner', net)

    def instantiate(self, **kwargs):
        return RecursiveLayer(
            self.net.instantiate(**kwargs),
            self.pointer
        )

    def forward(self, data_bag):
        limited = LimitedRecursiveLayer(self.net, DataPointer(self.pointer.data, self.pointer.nets + 1))
        # Add the link to itself to the begin of the list of nets
        net_args = DataBag(data_bag.data, [limited, *data_bag.nets])
        return limited(net_args)


class LimitedRecursiveLayer(FunctionalModule):
    """
    Recursive layer with limitation of the depth of recursion
    """

    DEPTH = 50

    def __init__(self, net, pointer):
        super().__init__()
        self.net = net
        self.depth = 0
        self.pointer = pointer
        self.add_module('net', net)

    def instantiate(self, **kwargs):
        raise AssertionError("LimitedRecursiveLayer shouldn't be instantiated as it's a 'virtual' layer")

    def forward(self, data_bag):
        self.depth += 1
        if self.depth > LimitedRecursiveLayer.DEPTH:
            # TODO: Do something better
            raise RecursionError('Too many recursion in a RecursiveLayer')
        return self.net(data_bag)


class GuardedLayer(FunctionalModule):
    """
    Net with many possible ways of execution, every possibility is chosen according on
    similarity of data and pattern
    """

    def __init__(self, to_type, cases):
        super().__init__()
        self.pointer = DataPointer.start
        self.type = to_type
        self.cases = cases
        for idx, case in enumerate(cases):
            self.add_module(str(idx), case)
            case.type = to_type

    def instantiate(self, **kwargs):
        return GuardedLayer(
            self.type.instantiate(**kwargs),
            list(map(lambda c: c.instantiate(**kwargs), self.cases)),
        )

    def forward(self, data_bag):
        results = [net(data_bag) for net in self.cases]
        result = results[0]
        for res in results[1:]:
            result += res
        return result

    class Case(FunctionalModule):

        EPS = 1e-4

        def __init__(self, pattern, net, to_type=None):
            super().__init__()
            self.pattern = pattern
            self.net = net
            self.add_module('net', net)
            self.type = to_type

        def instantiate(self, **kwargs):
            return GuardedLayer.Case(
                self.pattern,
                self.net.instantiate(**kwargs)
            )

        def forward(self, data_bag):
            (presence, trees) = self.pattern.get_trees(data_bag.data)
            execute_rows = presence.data > self.EPS

            if isinstance(self.type, RecSpec):
                # "Unwrap" recursive definition once
                type_spec = self.type.unwrap(1)
            else:
                type_spec = self.type
            result_length = type_spec.size()

            if execute_rows.any():
                # Case can be executed
                new_tensor = make_tuple(trees)
                # Select rows for execution
                rows = new_tensor.select_rows(execute_rows)
                new_data = DataBag(rows, data_bag.nets, execute_rows.sum().item())
                net_result = self.net(new_data)
                # Merge results

                # TODO: Implement something quicker
                to_cat = []
                ind = 0
                rows_count = execute_rows.size()[0]
                empty_row = TensorTree(
                    torch.zeros(1, result_length),
                    type_spec,
                    [None] * result_length,
                )
                for i in range(rows_count):
                    if (execute_rows[i] == 1).all():
                        mask = torch.zeros(net_result.tensor.size()[0], dtype=torch.uint8)
                        mask[ind] = 1
                        to_cat.append(net_result.select_rows(mask))
                        ind += 1
                    else:
                        to_cat.append(empty_row)
                result = stack(to_cat)

                return result
            else:
                # Pattern-matching didn't succeed - return empty
                return TensorTree(
                    torch.zeros(data_bag.size, result_length),
                    type_spec,
                    [None] * result_length,
                )


class ApplicationLayer(FunctionalModule):
    """
    Applies one network with results of others. The main building block of this architecture.

    Note that it's behaviour doesn't depend on the arguments and defined only in constructor
    """

    def __init__(self, operands, call=None, constants=None, data=None, nets=None):
        super().__init__()
        if call is None:
            call = []
        if constants is None:
            constants = []
        if data is None:
            data = []
        if nets is None:
            nets = []
        self.operands = operands
        self.call = call
        self.constants = constants
        self.data = data
        self.nets = nets
        for idx, operand in enumerate(operands):
            self.add_module(str(idx), operand)

    def instantiate(self, **kwargs):
        return ApplicationLayer(
            list(map(lambda o: o.instantiate(**kwargs), self.operands)),
            self.call,
            self.constants,
            self.data,
            self.nets,
        )

    def forward(self, data_bag):
        called = []
        for i in range(len(self.operands)):
            operand = self.operands[i]
            if i in self.call:
                operand = operand(data_bag)
            called.append(operand)
        for i in range(1, len(self.operands)):
            if i in self.constants:
                called[i] = called[i](data_bag)

        net = called[0]
        args = called

        data = []
        nets = []
        for i in range(1, len(args)):
            if i in self.data:
                data.append(args[i])
            if i in self.nets:
                nets.append(args[i])
        this_args = DataBag(make_tuple(data), nets, size=data_bag.size)
        net_args = data_bag.next_scope(net.pointer, this_args)
        return net(net_args)
