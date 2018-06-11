from .base import FunctionalModule


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
        return self.net.forward(*inputs)
