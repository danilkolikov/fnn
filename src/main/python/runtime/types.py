from abc import abstractmethod

import torch


class BaseTypeSpec:
    """
    Base specification of type.

    Type has size, start and end poisitons in structure of a bigger type,
    supports iteration over values and can be transformed to a tensor that
    contains all values of type as rows
    """

    def __init__(self, start, end, size=None):
        if size is None:
            size = end - start
        self.start = start
        self.end = end
        self.size = size

    @abstractmethod
    def __iter__(self):
        pass

    def to_tensor(self):
        return torch.cat(list(self))


class TypeSpec(BaseTypeSpec):
    """
    Object representing sum of types
    """

    def __init__(self, operands):
        super().__init__(operands[0].start, operands[-1].end)
        self.operands = operands

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        return torch.sum(presences, 1, keepdim=True)

    def __iter__(self):
        for operand in self.operands:
            for tensor in operand:
                to_cat = []
                if operand.start > self.start:
                    to_cat.append(torch.zeros([1, operand.start - self.start]))
                to_cat.append(tensor)
                if self.end > operand.end:
                    to_cat.append(torch.zeros([1, self.end - operand.end]))
                yield torch.cat(to_cat, 1)


class LitSpec(BaseTypeSpec):
    """
    Object representing type literal
    """

    def __init__(self, start):
        super().__init__(start, start + 1)

    def calc_presence(self, data, offset=0):
        presence = data.narrow(1, offset + self.start, 1)

        return presence

    def __iter__(self):
        yield torch.Tensor([[1]])


class ExtSpec(BaseTypeSpec):
    """
    Object representing occurrence of existing data type in the structure of other type
    """

    def __init__(self, spec, start):
        super().__init__(start, start + spec.size)
        self.spec = spec

    def calc_presence(self, data, offset=0):
        return self.spec.calc_presence(data, self.start + offset)

    def __iter__(self):
        for tensor in self.spec:
            yield tensor


class ProdSpec(BaseTypeSpec):
    """
    Object representing product of types
    """

    def __init__(self, operands, start):
        super().__init__(start, start + sum(map(lambda spec: spec.size, operands)))
        self.operands = operands

    def calc_presence(self, data, offset=0):
        presences = torch.cat([spec.calc_presence(data, offset) for spec in self.operands], 1)
        return torch.prod(presences, 1, keepdim=True)

    def __iter__(self):
        for tensor in ProdSpec._recursive_iter(self.operands, []):
            yield tensor

    @staticmethod
    def _recursive_iter(operands, collected):
        if len(operands) == 0:
            yield torch.cat(collected, 1)
        else:
            first = operands[0]
            rest = operands[1:]
            for tensor in first:
                new_list = [*collected, tensor]
                for result_tensor in ProdSpec._recursive_iter(rest, new_list):
                    yield result_tensor
