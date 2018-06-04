import torch

from .base import FunctionalModule
from ..data import DataPointer
from ..trees import SumTree, ProdTree


class ConstantLayer(FunctionalModule):
    """
    Returns constant value
    """

    def __init__(self, type_spec, position):
        super().__init__()
        self.position = position
        self.length = len(type_spec.operands)
        self.pointer = DataPointer.start  # Constants are defined in a global scope

    def forward(self, data_bag):
        # Construct constant value
        rows = data_bag.size
        tensor = torch.zeros(rows, self.length)
        tensor[:, self.position] = 1
        children = [None for _ in range(self.length)]

        return SumTree(tensor, children)


class ConstructorLayer(FunctionalModule):
    """
    Constructs object of one ADT from another and can't be learned
    """

    def __init__(self, to_type, position):
        super().__init__()
        self.length = len(to_type.operands)
        self.position = position
        self.pointer = DataPointer.start  # Constructors are defined at global scope

    def forward(self, data_bag):
        # Data is a Sum type with one operand - product of arguments
        data = data_bag.data
        size = data_bag.size
        product = data.children[0]
        assert isinstance(product, ProdTree)

        size_before = self.position
        size_after = self.length - (self.position + 1)
        to_cat = []
        if size_before > 0:
            to_cat.append(torch.zeros(size, size_before))
        to_cat.append(product.presence().view(size, 1))
        if size_after > 0:
            to_cat.append(torch.zeros(size, size_after))
        new_tensor = torch.cat(to_cat, 1)

        children = [*([None] * size_before), product, *([None] * size_after)]

        return SumTree(new_tensor, children)
