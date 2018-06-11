from .base import FunctionalModule
from ..data import DataPointer, DataBag


class RecursiveLayer(FunctionalModule):
    """
    Recursive network. Provides reference on itself for recursive calls.
    """
    DEPTH = 50

    def __init__(self, net, depth_handler, pointer, is_tail_recursive=False):
        super().__init__()
        self.net = net
        self.depth_handler = depth_handler
        self.pointer = pointer
        self.is_tail_recursive = is_tail_recursive
        self.add_module('inner', net)

    def forward(self, data_bag):
        layer = TailRecursiveLayer if self.is_tail_recursive else LimitedRecursiveLayer
        limited = layer(
            self.net,
            self.depth_handler,
            DataPointer(self.pointer.data, self.pointer.nets + 1)
        )
        # Add the link to itself to the begin of the list of nets
        net_args = DataBag(data_bag.data, [limited, *data_bag.nets])
        return limited.forward(net_args)


class LimitedRecursiveLayer(FunctionalModule):
    """
    General recursive layer with limitation of the depth of recursion
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
            _, args = data_bag.split(self.pointer)
            return self.depth_handler.forward(args)
        return self.net.forward(data_bag)


class TailRecursiveLayer(FunctionalModule):
    """
    Layer for tail recursion with higher limitation of the depth of recursion
    """

    DEPTH = 200

    def __init__(self, net, depth_handler, pointer):
        super().__init__()
        self.net = net
        self.depth_handler = depth_handler
        self.pointer = pointer
        self.add_module('net', net)

        self.stored_args = None
        self.in_recursion = False

    def forward(self, data_bag):
        if not self.in_recursion:
            self.in_recursion = True
            self.stored_args = data_bag
        else:
            self.stored_args = data_bag
            return None
        for i in range(self.DEPTH):
            result = self.net.forward(self.stored_args)
            if result is None:
                continue
            # The bottom reached
            self.in_recursion = False
            self.stored_args = None
            return result

        # The depth of recursion is too big, call the handler instead of itself
        args = self.stored_args
        _, args = args.split(self.pointer)
        self.in_recursion = False
        self.stored_args = None
        return self.depth_handler.forward(args)
