from abc import abstractmethod

from torch.nn import Module

from ..data import DataBag
from ..trees import make_tuple


class FunctionalModule(Module):
    """
    Base class for all functional modules. Supports forward propagation and calls with raw data array.

    Also module can be instantiated - type variables substituted by actual types.

    """

    @abstractmethod
    def forward(self, data_bag):
        pass

    def call(self, *trees, nets=None):
        if nets is None:
            nets = []
        if len(trees) == 1:
            trees = trees[0]
        if isinstance(trees, list):
            trees = make_tuple(trees)
        return self.forward(DataBag(trees, nets))
