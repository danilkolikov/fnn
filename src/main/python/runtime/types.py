from abc import abstractmethod
from itertools import islice

import torch


class BaseTypeSpec:
    """
    Base specification of type.

    Type has size, can be instantiated, supports iteration over values and
    can be transformed to a TensorTree that contains all values of type as rows.
    """

    @abstractmethod
    def instantiate(self, **kwargs):
        pass

    @abstractmethod
    def __iter__(self):
        pass

    def to_tensor_tree(self, count=None):
        from .tree import stack

        if count is None:
            values = list(self)
        else:
            values = list(islice(self, count))
        return stack(values)


class TypeSpec(BaseTypeSpec):
    """
    Object representing sum of types
    """

    def __init__(self, operands):
        self.operands = operands

    def size(self):
        size = 0
        for operand in self.operands:
            size += operand.size()
        return size

    def instantiate(self, **kwargs):
        return TypeSpec(list(map(lambda o: o.instantiate(**kwargs), self.operands)))

    def __iter__(self):
        from .tree import TensorTree

        cur_start = 0
        left = self.size()
        pos = 0
        for operand in self.operands:
            operand_size = operand.size()
            left -= operand_size
            for tree in operand:
                to_cat = []
                children = []
                if cur_start > 0:
                    to_cat.append(torch.zeros([1, cur_start]))
                    children.extend([None for _ in range(cur_start)])

                to_cat.append(tree.tensor)
                children.extend(tree.children)

                if left > 0:
                    to_cat.append(torch.zeros([1, left]))
                    children.extend([None for _ in range(left)])

                yield TensorTree(
                    torch.cat(to_cat, 1),
                    TypeSpec([*self.operands[:pos], tree.type, *self.operands[pos + 1:]]),
                    children,
                )
            cur_start += operand_size
            pos += 1

    def __repr__(self):
        return 'TypeSpec(' + str(self.operands) + ')'


class LitSpec(BaseTypeSpec):
    """
    Object representing type literal. It's size is 1 and literal doesn't depend on instantiation.
    """

    def instantiate(self, **kwargs):
        return self

    @staticmethod
    def size():
        return 1

    def __iter__(self):
        from .tree import TensorTree

        yield TensorTree(
            torch.Tensor([[1]]),
            self,
            [None]
        )

    def __repr__(self):
        return 'LitSpec()'


class VarSpec(BaseTypeSpec):
    """
    Object representing type variable. It has no size, cannot be iterated over, but can be instantiated
    """
    def __init__(self, name):
        self.name = name

    def instantiate(self, **kwargs):
        if self.name not in kwargs:
            return self
        return kwargs[self.name]

    def __iter__(self):
        raise ValueError("Can't iterate over a type variable " + self.name)

    def __repr__(self):
        return 'VarSpec(' + self.name + ')'


class RecSpec(BaseTypeSpec):
    """
    Recursive type spec. Allow type to refer to itself
    """
    def __init__(self, definition):
        self.definition = definition

    def instantiate(self, **kwargs):
        return RecSpec(lambda t: self.definition(t).instantiate(**kwargs))

    def unwrap(self, times):
        if times == 0:
            return create_empty_type()
        else:
            return self.definition(self.unwrap(times - 1))

    def __iter__(self):
        cur = create_empty_type()
        while True:
            cur = self.definition(cur)
            for tree in cur:
                yield tree

    def __repr__(self):
        return 'RecSpec(lambda t: ' + str(self.definition(VarSpec('t'))) + ')'


class ProdSpec(BaseTypeSpec):
    """
    Object representing product of types
    """

    def __init__(self, operands):
        self.operands = operands

    def instantiate(self, **kwargs):
        return ProdSpec(list(map(lambda o: o.instantiate(**kwargs), self.operands)))

    def size(self):
        return len(self.operands)

    def __iter__(self):
        for tree in self._recursive_iter(self.operands, [], []):
            yield tree

    def _recursive_iter(self, operands, collected, presences):
        from .tree import TensorTree

        if len(operands) == 0:
            presences = torch.stack(presences, 1)
            yield TensorTree(
                presences,
                self,
                collected
            )
        else:
            first = operands[0]
            rest = operands[1:]
            for tree in first:
                new_list = [*collected, tree]
                new_presences = [*presences, tree.presence()]
                for result_tree in self._recursive_iter(rest, new_list, new_presences):
                    yield result_tree

    def __repr__(self):
        return 'ProdSpec(' + str(self.operands) + ')'


def create_empty_type():
    return TypeSpec([])


def create_tuple_type(types):
    return TypeSpec([ProdSpec(types)])


def get_tuple_operands(type_spec):
    return type_spec.operands[0].operands
