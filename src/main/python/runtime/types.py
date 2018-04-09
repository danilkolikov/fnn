from abc import abstractmethod

import torch


class TypeSpec:
    def __init__(self, operands):
        self.operands = operands
        self.start = operands[0].start
        self.end = operands[-1].end
        self.size = self.end - self.start

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        return torch.sum(presences, 1, keepdim=True)


class LitSpec:
    def __init__(self, start):
        self.start = start
        self.end = start + 1
        self.size = 1

    def calc_presence(self, data, offset=0):
        presence = data.narrow(1, offset + self.start, 1)

        return presence


class ExtSpec:
    def __init__(self, spec, start):
        self.start = start
        self.spec = spec
        self.end = start + spec.size
        self.size = spec.size

    def calc_presence(self, data, offset=0):
        return self.spec.calc_presence(data, self.start + offset)


class ProdSpec:
    def __init__(self, operands, start):
        self.operands = operands
        self.start = start
        self.size = sum(map(lambda spec: spec.size, operands))
        self.end = self.start + self.size

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        return torch.prod(presences, 1, keepdim=True)


class LegacyTypeSpec:
    def __init__(self, start, end):
        self.start = start
        self.end = end
        self.size = end - start

    @abstractmethod
    def calc_presence(self, data, offset=0):
        pass


class LegacyLitSpec(LegacyTypeSpec):
    def __init__(self, name, start):
        super().__init__(start, start + 1)
        self.name = name

    def calc_presence(self, data, offset=0):
        presence = data.narrow(1, offset + self.start, 1)

        return presence


class LegacyExtSpec(LegacyTypeSpec):
    def __init__(self, spec, start):
        super().__init__(start, start + spec.size)
        self.spec = spec

    def calc_presence(self, data, offset=0):
        return self.spec.calc_presence(data, self.start + offset)


class LegacySumSpec(LegacyTypeSpec):
    def __init__(self, operands, start, end):
        super().__init__(start, end)
        self.operands = operands

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        return torch.sum(presences, 1, keepdim=True)


class LegacyProdSpec(LegacyTypeSpec):
    def __init__(self, name, operands, start, end):
        super().__init__(start, end)
        self.name = name
        self.operands = operands

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        return torch.prod(presences, 1, keepdim=True)
