import torch
from .trees import SumTree, ProdTree, empty_tree


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

    def __init__(self, data, nets=None, size=None):
        if nets is None:
            nets = []
        if size is None:
            size = data.tensor.size()[0]
        self.data = data
        self.nets = nets
        self.size = size

    def get_tree(self, pos):
        product = self.data.children[0]
        return product.children[pos]

    def get_net(self, pos):
        return self.nets[pos]

    def next_scope(self, pointer, data_bag):
        if pointer.data == 0:
            data = data_bag.data
        else:
            if self.size != data_bag.size:
                raise ValueError('Sizes of data bags are not equal - expected '
                                 + str(self.size) + ', but got ' + str(data_bag.size))
            # Type of data in tensor is tuple - Sum with one operand which is Product

            product = self.data.children[0]
            other_product = data_bag.data.children[0]

            old_data = product.tensor if product.tensor.dim() == 2 else product.tensor.unsqueeze(0)
            other_data = other_product.tensor if other_product.tensor.dim() == 2 else other_product.tensor.unsqueeze(0)
            data = torch.cat(
                [old_data.narrow(1, 0, pointer.data), other_data],
                1
            )
            new_children = [*self.data.children[:pointer.data], *data_bag.data.children]
            new_product = ProdTree(data, new_children)

            data = SumTree(new_product.presence().view(self.size, 1), [new_product])

        nets = [*self.nets[0:pointer.nets], *data_bag.nets]
        return DataBag(data, nets, data_bag.size)

    empty = None

    def __repr__(self):
        return "DataBag(" + str(self.data) + ", " + str(self.nets) + ")"


DataBag.empty = DataBag(empty_tree(), [])
