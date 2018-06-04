from abc import abstractmethod


class BaseTypeSpec:
    """
    Base specification of type.

    Type has size, can be instantiated, supports iteration over values and
    can be transformed to a TensorTree that contains all values of type as rows.
    """

    @abstractmethod
    def __eq__(self, o: object):
        pass


class TypeSpec(BaseTypeSpec):
    """
    Object representing sum of types
    """

    def __init__(self, operands):
        self.operands = operands

    def __eq__(self, o: object):
        return isinstance(o, TypeSpec) and self.operands == o.operands

    def __repr__(self):
        return 'TypeSpec(' + str(self.operands) + ')'


class LitSpec(BaseTypeSpec):
    """
    Object representing type literal. It's size is 1 and literal doesn't depend on instantiation.
    """

    def __eq__(self, o: object):
        # All LitSpecs are equal
        return isinstance(o, LitSpec)

    def __repr__(self):
        return 'LitSpec()'


class VarSpec(BaseTypeSpec):
    """
    Object representing type variable. It has no size, cannot be iterated over, but can be instantiated
    """
    def __init__(self, name):
        self.name = name

    def __eq__(self, o: object):
        return isinstance(o, VarSpec) and self.name == o.name

    def __repr__(self):
        return self.name


class ExtSpec(BaseTypeSpec):
    """
    Object representing type that was defined earlier. Can have parameters
    """

    def __init__(self, name, **kwargs):
        self.name = name
        self.args = kwargs

    def __eq__(self, o):
        return isinstance(o, ExtSpec) and self.name == o.name and self.args == o.args

    def __repr__(self):
        if len(self.args) == 0:
            return self.name
        return '(' + self.name + ' ' + ' '.join(self.args.values()) + ')'


class ProdSpec(BaseTypeSpec):
    """
    Object representing product of types
    """

    def __init__(self, operands):
        self.operands = operands

    def __eq__(self, o: object):
        return isinstance(o, ProdSpec) and self.operands == o.operands

    def __repr__(self):
        return 'ProdSpec(' + str(self.operands) + ')'


def create_empty_type():
    return TypeSpec([])


def create_unit_type():
    return TypeSpec([LitSpec()])


def create_tuple_type(types):
    return TypeSpec([ProdSpec(types)])
