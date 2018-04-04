from abc import abstractmethod

import torch


class TypeSpec:
    def __init__(self, start, end):
        self.start = start
        self.end = end
        self.size = end - start

    @abstractmethod
    def calc_presence(self, data, offset=0):
        pass


class LitSpec(TypeSpec):
    def __init__(self, name, start):
        super().__init__(start, start + 1)
        self.name = name

    def calc_presence(self, data, offset=0):
        presence = data.narrow(1, offset + self.start, 1)

        return presence


class ExtSpec(TypeSpec):
    def __init__(self, spec, start):
        super().__init__(start, start + spec.size)
        self.spec = spec

    def calc_presence(self, data, offset=0):
        return self.spec.calc_presence(data, self.start + offset)


class SumSpec(TypeSpec):
    def __init__(self, operands, start, end):
        super().__init__(start, end)
        self.operands = operands

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        # print(presences, torch.sum(presences, 1))
        return torch.sum(presences, 1, keepdim=True)


class ProdSpec(TypeSpec):
    def __init__(self, name, operands, start, end):
        super().__init__(start, end)
        self.name = name
        self.operands = operands

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        # print(presences)
        return torch.prod(presences, 1, keepdim=True)
