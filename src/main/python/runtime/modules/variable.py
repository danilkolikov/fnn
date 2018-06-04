from .base import FunctionalModule


class VariableLayer:
    """
    Gets value of some parameter from the data bag
    """

    class Data(FunctionalModule):
        """
        Returns a tree from the input DataBag
        """
        def __init__(self, position):
            super().__init__()
            self.position = position

        def forward(self, data_bag):
            return data_bag.get_tree(self.position)

    class Net(FunctionalModule):
        """
        Returns a network from the input DataBag
        """
        def __init__(self, position):
            super().__init__()
            self.position = position

        def forward(self, data_bag):
            return data_bag.get_net(self.position)

    class External(FunctionalModule):
        """
        Returns a network that was defined before it
        """
        def __init__(self, net):
            super().__init__()
            self.net = net
            self.add_module('external', net)

        def forward(self, data_bag):
            return self.net
