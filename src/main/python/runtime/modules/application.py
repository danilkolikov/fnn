from .base import FunctionalModule
from ..data import DataBag
from ..trees import make_tuple


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
                operand = operand.forward(data_bag)
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
        return net.forward(net_args)
