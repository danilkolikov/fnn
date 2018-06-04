from .base import FunctionalModule
from ..data import DataPointer, DataBag


class RecursiveLayer(FunctionalModule):
    """
    Recursive network. Provides reference on itself for recursive calls.
    """
    DEPTH = 50

    def __init__(self, net, depth_handler, pointer):
        super().__init__()
        self.net = net
        self.depth_handler = depth_handler
        self.pointer = pointer
        self.add_module('inner', net)

    def forward(self, data_bag):
        limited = LimitedRecursiveLayer(
            self.net,
            self.depth_handler,
            DataPointer(self.pointer.data, self.pointer.nets + 1)
        )
        # Add the link to itself to the begin of the list of nets
        net_args = DataBag(data_bag.data, [limited, *data_bag.nets])
        return limited.forward(net_args)


class LimitedRecursiveLayer(FunctionalModule):
    """
    Recursive layer with limitation of the depth of recursion
    """

    def __init__(self, net, depth_handler, pointer):
        super().__init__()
        self.net = net
        self.depth_handler = depth_handler
        self.depth = 0
        self.pointer = pointer
        self.add_module('net', net)

    def forward(self, data_bag):
        self.depth += 1
        if self.depth > RecursiveLayer.DEPTH:
            # If the depth of recursion is too big, call the handler instead of itself
            return self.depth_handler.forward(data_bag)
        return self.net.forward(data_bag)
