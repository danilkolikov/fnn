import torch

from .base import FunctionalModule
from ..trees import SumTree
from ..errors import UnsupportedTypeForZeroObjectCreation, UnknownType
from ..types import ExtSpec, TypeSpec


class ZeroLayer(FunctionalModule):
    """
    Layer that returns constant zero object of specified type
    """
    def __init__(self, defined_types, res_type):
        super(ZeroLayer, self).__init__()
        self.defined_types = defined_types
        self.res_type = res_type

    def forward(self, data_bag):
        if not isinstance(self.res_type, ExtSpec):
            raise UnsupportedTypeForZeroObjectCreation(self.res_type)
        if self.res_type.name not in self.defined_types:
            raise UnknownType(self.res_type.name)
        res_type = self.defined_types[self.res_type.name]
        if not isinstance(res_type, TypeSpec):
            raise UnsupportedTypeForZeroObjectCreation(res_type)
        object_size = len(res_type.operands)
        zero_tensor = torch.zeros(data_bag.size, object_size)
        zero_children = [None] * object_size
        return SumTree(zero_tensor, zero_children)

    @staticmethod
    def bind_defined_types(defined_type):
        def make_layer(res_type):
            return ZeroLayer(defined_type, res_type)
        return make_layer
