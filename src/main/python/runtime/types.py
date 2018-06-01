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
    def size(self):
        pass

    @abstractmethod
    def specs(self):
        pass

    @abstractmethod
    def objects(self):
        raise StopIteration

    @abstractmethod
    def __eq__(self, o: object):
        pass

    def to_tensor_tree(self, count=None):
        from .tree import stack

        if count is None:
            values = list(self.objects())
        else:
            values = list(islice(self.objects(), count))
        return stack(values)


class TypeSpec(BaseTypeSpec):
    """
    Object representing sum of types
    """

    def __init__(self, operands):
        self.operands = operands
        self.wrapped = None

    def size(self):
        size = 0
        for operand in self.operands:
            size += operand.size()
        return size

    def instantiate(self, **kwargs):
        return TypeSpec([o.instantiate(**kwargs) for o in self.operands])

    def __eq__(self, o: object):
        if not isinstance(o, TypeSpec):
            if isinstance(o, RecSpec) and self.wrapped is not None:
                return self.wrapped == o
            return False
        if self.wrapped is not None and o.wrapped is not None:
            return self.wrapped == o.wrapped
        return self.operands == o.operands

    def specs(self):
        for operand in self.operands:
            for spec in operand.specs():
                yield spec

    def objects(self):
        from .tree import TensorTree

        cur_start = 0
        left = self.size()
        pos = 0
        for operand in self.operands:
            operand_size = operand.size()
            left -= operand_size
            for tree in operand.objects():
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

    def size(self):
        return 1

    def __eq__(self, o: object):
        # All LitSpecs are equal
        return isinstance(o, LitSpec)

    def specs(self):
        yield self

    def objects(self):
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

    def size(self):
        return 1

    def __eq__(self, o: object):
        # Every VarSpec is equal (alpha-equivalent, to be precise)
        return isinstance(o, VarSpec)

    def specs(self):
        yield self

    def objects(self):
        raise StopIteration

    def __repr__(self):
        return 'VarSpec(' + self.name + ')'


class RecSpec(BaseTypeSpec):
    """
    Recursive type spec. Allow type to refer to itself
    """
    def __init__(self, name, definition):
        self.name = name
        self.definition = definition

    def instantiate(self, **kwargs):
        return RecSpec(self.name, lambda t: self.definition(t).instantiate(**kwargs))

    def size(self):
        raise ValueError("Can't get size of RecType as it's structure may be infinite")

    def specs(self):
        raise ValueError("RecSpec doesn't support iteration over specs")

    def __eq__(self, o: object):
        if not isinstance(o, RecSpec):
            if isinstance(o, TypeSpec) and o.wrapped is not None:
                return self == o.wrapped
            return False
        # Unwrap once and compare results
        return self.definition(VarSpec(self.name)) == o.definition(VarSpec(self.name))

    def unwrap(self, times):
        if times == 0:
            return self
        else:
            unwrapped = self.definition(self.unwrap(times - 1))
            unwrapped.wrapped = self
            return unwrapped

    def objects(self):
        raise StopIteration

    def __repr__(self):
        return 'RecSpec(lambda ' + self.name + ': ' + str(self.definition(VarSpec(self.name))) + ')'


class ProdSpec(BaseTypeSpec):
    """
    Object representing product of types
    """

    def __init__(self, operands):
        self.operands = operands

    def instantiate(self, **kwargs):
        return ProdSpec([o.instantiate(**kwargs) for o in self.operands])

    def __eq__(self, o: object):
        if not isinstance(o, ProdSpec):
            return False
        return self.operands == o.operands

    def size(self):
        return len(self.operands)

    def objects(self):
        for tree in self._recursive_iter(self.operands, [], []):
            yield tree

    def specs(self):
        for operand in self.operands:
            # Don't iterate over operand's specs
            yield operand

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
            for tree in first.objects():
                new_list = [*collected, tree]
                new_presences = [*presences, tree.presence()]
                for result_tree in self._recursive_iter(rest, new_list, new_presences):
                    yield result_tree

    def __repr__(self):
        return 'ProdSpec(' + str(self.operands) + ')'


def create_empty_type():
    return TypeSpec([])


def create_unit_type():
    return TypeSpec([LitSpec()])


def create_tuple_type(types):
    return TypeSpec([ProdSpec(types)])


def get_tuple_operands(type_spec):
    return type_spec.operands[0].operands
