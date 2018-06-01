from abc import abstractmethod

import torch
from torch.nn import Module, Parameter

from .data import DataBag, DataPointer
from .tree import TensorTree, OperatorTree, stack, make_tuple
from .types import TypeSpec, ProdSpec, LitSpec, RecSpec, VarSpec, create_tuple_type
from .functions import typedSigmoid

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
        tensor = torch.zeros(rows, size, requires_grad=False)
        tensor[:, self.position] = 1
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
        data_type = TypeSpec([*type_operands_before, *data.type.operands, *type_operands_after])

        size_before = sum(map(lambda t: t.size(), type_operands_before))
        size_after = sum(map(lambda t: t.size(), type_operands_after))

        to_cat = []
        if size_before > 0:
            to_cat.append(torch.zeros(data_bag.size, size_before))
        to_cat.append(data_bag.data.tensor)
        if size_after > 0:
            to_cat.append(torch.zeros(data_bag.size, size_after))
        new_tensor = torch.cat(to_cat, 1)

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
    DEPTH = 50

    def __init__(self, net, result_type, pointer):
        super().__init__()
        self.net = net
        self.type = result_type
        self.pointer = pointer
        self.add_module('inner', net)

    def instantiate(self, **kwargs):
        return RecursiveLayer(
            self.net.instantiate(**kwargs),
            self.type.instantiate(**kwargs),
            self.pointer
        )

    def forward(self, data_bag):
        limited = LimitedRecursiveLayer(self.net, DataPointer(self.pointer.data, self.pointer.nets + 1), self.type)
        # Add the link to itself to the begin of the list of nets
        net_args = DataBag(data_bag.data, [limited, *data_bag.nets])
        return limited(net_args)


class LimitedRecursiveLayer(FunctionalModule):
    """
    Recursive layer with limitation of the depth of recursion
    """

    def __init__(self, net, pointer, type):
        super().__init__()
        self.net = net
        self.depth = 0
        self.pointer = pointer
        self.type = type.unwrap(1) if isinstance(type, RecSpec) else type
        self.add_module('net', net)

    def instantiate(self, **kwargs):
        raise AssertionError("LimitedRecursiveLayer shouldn't be instantiated as it's a 'virtual' layer")

    def forward(self, data_bag):
        self.depth += 1
        if self.depth > RecursiveLayer.DEPTH:
            result_size = self.type.size()
            return TensorTree(
                torch.zeros(data_bag.size, result_size),
                self.type,
                [None] * result_size,
            )
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
            result = result + res
        return result

    class Case(FunctionalModule):

        EPS = 1e-3
        SELECT_ROWS = False

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

                if GuardedLayer.Case.SELECT_ROWS:
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
                            mask = torch.zeros(net_result.tensor.size()[0], dtype=torch.uint8, requires_grad=False)
                            mask[ind] = 1
                            to_cat.append(net_result.select_rows(mask))
                            ind += 1
                        else:
                            to_cat.append(empty_row)
                    result = stack(to_cat)
                else:
                    new_data = DataBag(new_tensor, data_bag.nets, data_bag.size)
                    result = self.net(new_data)
                return result.cmul(presence.view(data_bag.size, 1))
            else:
                # Pattern-matching didn't succeed - return empty object
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


class TrainableLayer(FunctionalModule):
    """
    Layer of the network that can be fitted to data.
    """

    def __init__(self, arguments, to_type, from_depth=1, to_depth=1,
                 weights=None, weights_mask=None, bias=None):
        super().__init__()
        self.pointer = DataPointer.start  # Trainable networks only use specified arguments
        self.arguments = arguments

        self.from_depth = from_depth
        self.to_depth = to_depth

        # Unwrap type of arguments, if they are recursive, an wrap them in tuple type
        self.from_type = create_tuple_type(
            [arg.unwrap(self.from_depth) if isinstance(arg, RecSpec) else arg for arg in self.arguments]
        )
        # Unwrap type of result, if it's recursive, and wrap it in a tuple type
        self.base_to_type = to_type
        self.to_type = create_tuple_type([
            to_type.unwrap(self.to_depth) if isinstance(to_type, RecSpec) else to_type
        ])
        if weights is None:
            weights = TrainableLayer._create_weights(
                self.from_type,
                self.to_type,
                self.from_depth,
                self.to_depth
            )
        if weights_mask is None:
            weights_mask = TrainableLayer._create_weight_mask(
                self.from_type,
                self.to_type,
                self.from_depth,
                self.to_depth
            )
        if bias is None:
            bias = TrainableLayer._create_bias(self.to_type, self.to_depth)

        self.weights = weights
        self.weight_mask = weights_mask
        self.bias = bias

        # Register parameters
        self._register_operator_parameters([], self.weights)
        self._register_tree_parameters('', [], self.bias)

    def _register_operator_parameters(self, path, operator):
        self._register_tree_parameters(str(path) + '_w', [], operator.tree)
        for (i, child) in enumerate(operator.children):
            if child is not None:
                self._register_operator_parameters([*path, i], child)

    def _register_tree_parameters(self, prefix, path, tree):
        self.register_parameter(prefix + '_' + str(path), tree.tensor)
        for (i, child) in enumerate(tree.children):
            if child is not None:
                self._register_tree_parameters(prefix, [*path, i], child)

    def instantiate(self, **kwargs):
        return TrainableLayer(
            [arg.instantiate(**kwargs) for arg in self.arguments],
            self.base_to_type.instantiate(**kwargs),
            self.from_depth,
            self.to_depth,
            self.weights.instantiate(**kwargs),
            self.weight_mask.instantiate(**kwargs),
            self.bias.instantiate(**kwargs)
        )

    def forward(self, data_bag):
        weights = self.weights.pointwise_mul(self.weight_mask)

        linear = weights.tree_mul(data_bag.data) + self.bias
        # print(data_bag.data, linear)
        # Result of linear combination is contained in the 0-th child
        child = linear.children[0]
        if child is not None:
            result = child.apply_typed_activation(typedSigmoid)
            return result
        else:
            # Can't create such object
            result_type = next(self.to_type.specs())
            result_size = result_type.size()
            return TensorTree(
                torch.zeros(data_bag.size, result_size),
                result_type,
                [None] * result_size
            )

    @staticmethod
    def _build_operator(from_type, to_type, from_depth, to_depth, tensor_builder):
        """
        Builds OperatorTree using specified type information, depth and builder of tensor for one layer

        :param from_type: Type of structure of OperatorTree
        :param to_type: Type of TreeTensors in OperatorTree
        :param from_depth: Depth of OperatorTree
        :param to_depth: Depth of TreeTensors in OperatorTree
        :param tensor_builder: Builder of tensors
        :return: OperatorTree
        """

        from_size = from_type.size()
        weight = TrainableLayer._build_tree(
            from_size,
            from_type,
            to_type,
            to_depth,
            tensor_builder
        )
        if isinstance(from_type, VarSpec):
            return OperatorTree(weight, [])

        if from_depth == 0:
            children = [None] * from_size
        else:
            children = []
            for operand in from_type.operands:
                if isinstance(operand, LitSpec):
                    children.append(None)
                    continue
                if isinstance(operand, ProdSpec):
                    for prod_operand in operand.operands:
                        if isinstance(prod_operand, TypeSpec):
                            operator = TrainableLayer._build_operator(
                                prod_operand,
                                to_type,
                                from_depth - 1,
                                to_depth,
                                tensor_builder
                            )
                        else:
                            operator = None
                        children.append(operator)

        return OperatorTree(weight, children)

    @staticmethod
    def _build_tree(from_size, from_type, to_type, to_depth, tensor_builder):
        """
        Builds TensorTree using specified type information and builder of tensor

        :param from_size: Precomputed size of from_type
        :param from_type: Type of structure of this TreeTensor belongs to
        :param to_type: Type of TreeTensor
        :param to_depth: Depth of TreeTensor
        :param tensor_builder: Builder of tensor
        :return: TreeTensor
        """

        to_size = to_type.size()
        tensor = tensor_builder(from_type, to_type, from_size, to_size)

        if isinstance(to_type, VarSpec):
            return TensorTree(tensor, to_type, [])

        if to_depth == 0:
            children = [None] * to_size
            operands = to_type.operands
        else:
            # Unwrap one layer of to_type and create children
            children = []
            operands = []
            for operand in to_type.operands:
                if isinstance(operand, LitSpec):
                    children.append(None)
                    operands.append(operand)
                    continue
                if isinstance(operand, ProdSpec):
                    prod_operands = []
                    for prod_operand in operand.operands:
                        if isinstance(prod_operand, VarSpec):
                            # Don't create additional layers for variables
                            children.append(None)
                            prod_operands.append(prod_operand)
                            continue
                        if isinstance(prod_operand, TypeSpec):
                            child = TrainableLayer._build_tree(
                                from_size,
                                from_type,
                                prod_operand,
                                to_depth - 1,
                                tensor_builder,
                            )
                            children.append(child)
                            prod_operands.append(child.type)
                    operands.append(ProdSpec(prod_operands))
                    continue
                raise ValueError('Unexpected TypeSpec: ' + str(operand))

        res_type = TypeSpec(operands)
        return TensorTree(tensor, res_type, children)

    @staticmethod
    def _create_weights(from_type, to_type, from_depth, to_depth):
        """
        Creates trainable OperatorTree and fills it with random values
        """

        def _create_tensors(this_from_type, this_to_type, from_size, to_size):
            return Parameter(torch.randn(from_size, to_size), requires_grad=True)

        return TrainableLayer._build_operator(from_type, to_type, from_depth, to_depth, _create_tensors)

    @staticmethod
    def _create_weight_mask(from_type, to_type, from_depth, to_depth):
        def _create_tensors(this_from_type, this_to_type, from_size, to_size):
            if isinstance(this_to_type, VarSpec):
                if isinstance(this_from_type, VarSpec):
                    # a->a holds for every a, so mask is equal to 1
                    return torch.ones(1, 1)
                else:
                    # We can't create object of variable type from not-variables from any type
                    return torch.zeros(1, to_size)

            mask = torch.zeros(from_size, to_size, requires_grad=False)

            # Let's set values of the mask. We will use axioms of logic for it:
            # (a -> T) holds for every a, so we can create a literal using any object - set mask to 1
            # (a -> a) holds for every a, so we can create an object using object of it's type - set mask to 1
            # (a -> b) is false for a != b, so we can't create an object of one type using object of other type
            #   - set mask to 0

            to_cur = 0
            for to_operand in this_to_type.operands:
                if isinstance(to_operand, LitSpec):
                    # Situation (a->T) - assign 1 to mask
                    mask[:, to_cur] = 1
                    to_cur += 1
                    continue
                if isinstance(to_operand, ProdSpec):
                    for to_prod_operand in to_operand.operands:
                        # Find operands in the structure of the `from_type` with the same type
                        if isinstance(this_from_type, VarSpec):
                            if to_prod_operand == this_from_type:
                                # a->a situation
                                mask[0, to_cur] = 1
                            to_cur += 1
                            continue
                        from_cur = 0
                        for from_operand in this_from_type.operands:
                            if isinstance(from_operand, LitSpec):
                                from_cur += 1
                                continue
                            if isinstance(from_operand, ProdSpec):
                                for from_prod_operand in from_operand.operands:
                                    if to_prod_operand == from_prod_operand:
                                        # Types are equal - situation (a->a) - assign mask to 1
                                        mask[from_cur, to_cur] = 1
                                    else:
                                        # Types are different - situation (a->b) - mask will be 0
                                        pass
                                    from_cur += 1
                        to_cur += 1
            return mask

        return TrainableLayer._build_operator(from_type, to_type, from_depth, to_depth, _create_tensors)

    @staticmethod
    def _create_bias(to_type, to_depth):
        """
        Creates trainable TensorTree and fills it with random values
        """
        def _create_tensors(this_from_type, this_to_type, from_size, to_size):
            return Parameter(torch.randn(from_size, to_size), requires_grad=True)

        return TrainableLayer._build_tree(1, None, to_type, to_depth, _create_tensors)