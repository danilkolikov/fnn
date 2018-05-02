import torch
from .types import create_tuple_type, get_tuple_operands
from .tree import TensorTree, empty_tree


class DataPointer:
    """
    Points of some location in DataBag
    """

    def __init__(self, data, nets):
        self.data = data
        self.nets = nets

    start = None


DataPointer.start = DataPointer(0, 0)


class DataBag:
    """
    Contains information required for execution of the network
    """

    def __init__(self, data, nets, size=None):
        if size is None:
            size = data.tensor.size()[0]
        self.data = data
        self.nets = nets
        self.size = size

    def get_net(self, pos):
        return self.nets[pos]

    def next_scope(self, pointer, data_bag):
        if pointer.data == 0:
            data = data_bag.data
        else:
            if self.size != data_bag.size:
                raise ValueError('Sizes of data bags are not equal - expected '
                                 + str(self.size) + ', but got ' + str(data_bag.size))
            # Type of data in tensor is tuple - Sum with one summand which is Product
            data_type = self.data.type
            operands = get_tuple_operands(data_type)[:pointer.data]
            new_operands = get_tuple_operands(data_bag.data.type)

            old_data = self.data if self.data.dim() == 2 else self.data.unsqueeze(0)
            data = torch.cat(
                [old_data.narrow(1, 0, pointer.data), data_bag.data],
                1
            )
            new_children = [*self.data.children[:pointer.data], *data_bag.data.children]
            data_type = create_tuple_type([*operands, *new_operands])
            data = TensorTree(data, data_type, new_children)

        nets = [*self.nets[0:pointer.nets], *data_bag.nets]
        return DataBag(data, nets, data_bag.size)

    empty = None

    def __repr__(self):
        return "DataBag(" + str(self.data) + ", " + str(self.nets) + ")"


DataBag.empty = DataBag(empty_tree(), [])
