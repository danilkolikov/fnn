from abc import abstractmethod


class Pattern:
    """
    Base class for pattern for pattern-matching
    """

    @abstractmethod
    def calc_presence(self, data):
        pass


class ObjectPattern(Pattern):
    """
    Pattern that represents occurrence of some type literal, for example `False`
    """

    def __init__(self, name, position):
        self.name = name
        self.position = position

    def calc_presence(self, data):
        return data.narrow(-1, self.position, 1)


class VariablePattern(Pattern):
    """
    Pattern that represents occurrence of a variable in object, for example `Just x`
    """

    def __init__(self, name, type_spec, start, end):
        self.name = name
        self.type_spec = type_spec
        self.start = start
        self.size = end - start

    def calc_presence(self, data):
        var = data.narrow(-1, self.start, self.size)
        return self.type_spec.calc_presence(var)
