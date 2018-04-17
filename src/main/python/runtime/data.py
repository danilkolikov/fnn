import torch


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

    def __init__(self, data, nets):
        self.data = data
        self.nets = nets

    def get_net(self, pos):
        return self.nets[pos]

    def next_scope(self, pointer, data_bag):
        if pointer.data == 0:
            data = data_bag.data
        else:
            old_data = self.data if self.data.dim() == 2 else self.data.unsqueeze(0)
            data = torch.cat(
                [old_data.narrow(1, 0, pointer.data), data_bag.data],
                1
            )
        nets = [*self.nets[0:pointer.nets], *data_bag.nets]
        return DataBag(data, nets)

    def before(self, pointer):
        return DataBag(
            self.data.narrow(1, 0, pointer.data),
            self.nets[0:pointer.nets]
        )

    def append(self, data_bag):
        return DataBag(
            torch.cat(self.data, data_bag.data, 1),
            [*self.nets, *data_bag.nets]
        )

    empty = None

    def __repr__(self):
        return "DataBag(" + str(self.data) + ", " + str(self.nets) + ")"


DataBag.empty = DataBag(torch.zeros(0), [])
