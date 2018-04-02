from abc import abstractmethod

import torch


class TypeSpec:
    def __init__(self, start, end):
        self.start = start
        self.end = end
        self.size = end - start

    @abstractmethod
    def calc_presence(self, data):
        pass


class LitSpec(TypeSpec):
    def __init__(self, name, start):
        super().__init__(start, start + 1)
        self.name = name

    def calc_presence(self, data):
        presence = data.narrow(1, self.start, 1)
        # print(self.name, presence)

        return presence


class SumSpec(TypeSpec):
    def __init__(self, operands, start, end):
        super().__init__(start, end)
        self.operands = operands

    def calc_presence(self, data):
        presences = torch.cat([spec.calc_presence(data) for spec in self.operands], 1)
        # print(presences, torch.sum(presences, 1))
        return torch.sum(presences, 1)


class ProdSpec(TypeSpec):
    def __init__(self, name, operands, start, end):
        super().__init__(start, end)
        self.name = name
        self.operands = operands

    def calc_presence(self, data):
        presences = torch.cat([spec.calc_presence(data) for spec in self.operands], 1)
        # print(presences)
        return torch.prod(presences, 1)
