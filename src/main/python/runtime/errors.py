
class UnexpectedTreeTypes(ValueError):
    def __init__(self, a, b):
        super(UnexpectedTreeTypes, self).__init__('Unexpected tree types: ' + str(type(a)) + ' and ' + str(type(b)))


class UnexpectedTypeSpec(ValueError):
    def __init__(self, spec):
        super(UnexpectedTypeSpec, self).__init__('Unexpected TypeSpec: ' + str(spec))


class UnknownType(ValueError):
    def __init__(self, name):
        super(UnknownType, self).__init__('Unknown Type: ' + name)