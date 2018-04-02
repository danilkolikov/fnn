import torch


class DataPointer:
    def __init__(self, data, nets):
        self.data = data
        self.nets = nets

    start = None


DataPointer.start = DataPointer(0, 0)


class DataBag:
    def __init__(self, data, nets):
        self.data = data
        self.nets = nets

    def get_net(self, pos):
        return self.nets[pos]

    def next_scope(self, pointer, data_bag):
        if pointer.data == 0:
            data = data_bag.data
        else:
            data = torch.cat(
                [self.data.narrow(1, 0, pointer.data), data_bag.data],
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


DataBag.empty = DataBag(torch.zeros(0), [])
