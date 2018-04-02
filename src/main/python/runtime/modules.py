import torch
from torch.nn import Module, Linear
from torch.nn.parameter import Parameter

from runtime.data import DataBag, DataPointer


class ConstantLayer(Module):
    """
    Returns constant value
    """

    def __init__(self, value):
        super().__init__()
        self.value = Parameter(value, requires_grad=False)

    def forward(self, data_bag):
        return self.value.repeat(data_bag.data.size()[0], 1)


class TeachableLayer(Module):
    """
    Constructs one data-type from another and can be learned
    """

    def __init__(self, from_type, to_type):
        super().__init__()
        self.layer = Linear(from_type.size(), to_type.size())
        self.from_type = from_type
        self.to_type = to_type
        self.pointer = DataPointer.start    # Constructors are declared on the global scope

    def forward(self, data_bag):
        linear_output = self.layer(data_bag.data)
        return linear_output


class ConstructorLayer(Module):
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
            before = torch.autograd.Variable(torch.zeros(self.offset, *data.size()[1:]))
            to_cat.append(before)
        to_cat.append(data)
        if zeros_after > 0:
            after = torch.autograd.Variable(torch.zeros(zeros_after, *data.size()[1:]))
            to_cat.append(after)
        return torch.cat(to_cat)


class VariableLayer:
    """
    Gets value of some parameter from data bag
    """

    class Data(Module):
        def __init__(self, start, end):
            super().__init__()
            self.start = start
            self.len = end - start

        def forward(self, data_bag):
            result = data_bag.data.narrow(1, self.start, self.len)
            return result

    class Net(Module):
        def __init__(self, pos):
            super().__init__()
            self.pos = pos

        def forward(self, data_bag):
            return data_bag.get_net(self.pos)

    class External(Module):
        def __init__(self, net):
            super().__init__()
            self.net = net

        def forward(self, data_bag):
            return self.net(data_bag)


class AnonymousNetLayer(Module):
    """
    Net that was anonymously declared. Keeps pointer on it's scope
    """

    def __init__(self, net, pointer):
        super().__init__()
        self.net = net
        self.pointer = pointer

    def forward(self, *inputs):
        return self.net(*inputs)


class GuardedLayer(Module):
    """
    Net with many possible ways of execution, every possibility is chosen according on
    similarity of data and pattern
    """

    def __init__(self, cases):
        super().__init__()
        self.pointer = DataPointer.start
        self.cases = cases

    def forward(self, data_bag):
        results = [net(data_bag) for net in self.cases]
        # print(results)
        result = results[0]
        for res in results[1:]:
            result = torch.add(result, 1, res)
        return result

    class Case(Module):
        def __init__(self, patterns, net):
            super().__init__()
            self.patterns = patterns
            self.net = net

        def forward(self, data_bag):
            if len(self.patterns) == 0:
                return self.net(data_bag)
            patterns = torch.cat([pattern.calc_presence(data_bag.data) for pattern in self.patterns], 1)
            presence = torch.prod(patterns, 1).unsqueeze(1)
            result = self.net(data_bag)
            # print(presence.size(), result.size())

            # print("case", patterns, "pres", presence, "res", result, "mult", presence*result)
            return presence * result


class ApplicationLayer(Module):
    """
    Applies one network with results of others. The main building block of this architecture.

    Note that it's behaviour doesn't depend on the arguments and defined only in constructor
    """

    def __init__(self, operands, call, constants, data, nets):
        super().__init__()
        self.operands = operands
        self.call = call
        self.constants = constants
        self.data = data
        self.nets = nets

    def forward(self, data_bag):
        called = []
        for i in range(len(self.operands)):
            operand = self.operands[i]
            if i in self.call:
                operand = operand(data_bag)
            if i in self.constants:
                operand = operand(data_bag)
            called.append(operand)

        net = called[0]
        args = called[1:]

        data = []
        nets = []
        for i in range(len(args)):
            if i in self.data:
                data.append(args[i])
            if i in self.nets:
                nets.append(args[i])
        this_args = DataBag(torch.cat(data, 1), nets)
        net_args = data_bag.next_scope(net.pointer, this_args)

        return net(net_args)
