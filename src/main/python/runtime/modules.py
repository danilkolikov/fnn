import torch
from torch.nn import Module, Linear
from torch.autograd import Variable
from torch.nn.functional import sigmoid
from abc import abstractmethod

from .data import DataBag, DataPointer


class FunctionalModule(Module):
    """
    Base class for all functional modules. Supports forward propagation and calls with raw data array

    """

    @abstractmethod
    def forward(self, data_bag):
        pass

    def call(self, data, nets=None):
        if nets is None:
            nets = []
        data = Variable(data, requires_grad=False)
        return self(DataBag(data, nets))


class ConstantLayer(FunctionalModule):
    """
    Returns constant value
    """

    def __init__(self, size, position):
        super().__init__()
        self.value = Variable(
            torch.Tensor([1 if i == position else 0 for i in range(size)]),
            requires_grad=False
        )
        self.pointer = DataPointer.start    # Constants are defined in a global scope

    def forward(self, data_bag):
        return self.value.resize(1, self.value.size()[0])


class TrainableLayer(FunctionalModule):
    """
    Constructs one data-type from another and can be learned
    """

    def __init__(self, from_type_size, to_type_spec):
        super().__init__()
        self.layer = Linear(from_type_size, to_type_spec.size)
        self.from_type_size = from_type_size
        self.to_type_spec = to_type_spec
        self.pointer = DataPointer.start    # Constructors are declared on the global scope

    def forward(self, data_bag):
        linear_output = self.layer(data_bag.data)
        # ToDo: Implement fancy type-based activation function
        return sigmoid(linear_output)


class ConstructorLayer(FunctionalModule):
    """
    Constructs one data-type from another and can't be learned
    """

    def __init__(self, from_type_size, to_type_size, offset):
        super().__init__()
        self.from_type_size = from_type_size
        self.to_type_size = to_type_size
        self.offset = offset
        self.pointer = DataPointer.start

    def forward(self, data_bag):
        data = data_bag.data
        zeros_after = self.to_type_size - (self.offset + self.from_type_size)
        to_cat = []
        if self.offset > 0:
            before = torch.autograd.Variable(torch.zeros(data.size()[0], self.offset))
            to_cat.append(before)
        to_cat.append(data)
        if zeros_after > 0:
            after = torch.autograd.Variable(torch.zeros(data.size()[0], zeros_after))
            to_cat.append(after)
        return torch.cat(to_cat, 1)


class VariableLayer:
    """
    Gets value of some parameter from data bag
    """

    class Data(FunctionalModule):
        def __init__(self, start, end):
            super().__init__()
            self.start = start
            self.len = end - start

        def forward(self, data_bag):
            result = data_bag.data.narrow(1, self.start, self.len)
            return result

    class Net(FunctionalModule):
        def __init__(self, pos):
            super().__init__()
            self.pos = pos

        def forward(self, data_bag):
            return data_bag.get_net(self.pos)

    class External(FunctionalModule):
        def __init__(self, net):
            super().__init__()
            self.net = net
            self.add_module('external', net)

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

    def forward(self, data_bag):
        limited = LimitedRecursiveLayer(self.net, DataPointer(self.pointer.data, self.pointer.nets + 1))
        net_args = DataBag(data_bag.data, [*data_bag.nets, limited])
        return limited(net_args)


class LimitedRecursiveLayer(FunctionalModule):
    """
    Recursive layer with limitation of the depth of recursion
    """

    DEPTH = 10

    def __init__(self, net, pointer):
        super().__init__()
        self.net = net
        self.depth = 0
        self.pointer = pointer
        self.add_module('net', net)

    def forward(self, data_bag):
        self.depth += 1
        if self.depth > LimitedRecursiveLayer.DEPTH:
            return Variable(
                torch.zeros([data_bag.data.size()[0], 1]),
                requires_grad=False
            )
        return self.net(data_bag)


class GuardedLayer(FunctionalModule):
    """
    Net with many possible ways of execution, every possibility is chosen according on
    similarity of data and pattern
    """

    def __init__(self, cases):
        super().__init__()
        self.pointer = DataPointer.start
        self.cases = cases
        for idx, case in enumerate(cases):
            self.add_module(str(idx), case)

    def forward(self, data_bag):
        results = [net(data_bag) for net in self.cases]
        result = results[0]
        for res in results[1:]:
            result = torch.add(result, 1, res)
        return result

    class Case(Module):

        EPS = 1e-4

        def __init__(self, patterns, net):
            super().__init__()
            self.patterns = patterns
            self.net = net
            self.add_module('net', net)

        def forward(self, data_bag):
            if len(self.patterns) == 0:
                return self.net(data_bag)

            presences = [pattern.calc_presence(data_bag.data) for pattern in self.patterns]
            patterns = torch.cat(presences, 1)
            presence = torch.prod(patterns, 1).unsqueeze(1)
            execute_rows = presence.data > self.EPS
            if execute_rows.any():
                # Case can be executed
                rows = data_bag.data[execute_rows].view(execute_rows.sum(), data_bag.data.size()[1])
                new_data = DataBag(rows, data_bag.nets)
                net_result = self.net(new_data)

                # TODO: Implement something quicker
                zero = Variable(torch.Tensor([0]), requires_grad=False).repeat(net_result.size()[1])
                to_cat = []
                ind = 0
                for i in range(execute_rows.size()[0]):
                    if (execute_rows[i] == 1).all():
                        to_cat.append(net_result[ind])
                        ind += 1
                    else:
                        to_cat.append(zero)
                result = torch.stack(to_cat)
                return presence * result
            else:
                # Pattern-matching didn't succeed - return 0
                return Variable(torch.Tensor([0]), requires_grad=False)


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
        this_args = DataBag(torch.cat(data, 1) if len(data) > 0 else torch.Tensor(0), nets)
        net_args = data_bag.next_scope(net.pointer, this_args)
        return net(net_args)
