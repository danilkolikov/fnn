from abc import abstractmethod
import torch


class BasePattern:
    """
    Base class for patterns
    """

    @abstractmethod
    def get_trees(self, tree):
        """
        Get TreeTensors according to a pattern from the tree

        :param tree: TreeTensor
        :return: Tuple - first element is presence of elements in a tree, second - list of trees
        """
        pass


class ConstructorPattern(BasePattern):
    """
    Pattern that represents application of a type constructor
    """
    def __init__(self, position, operands):
        self.position = position
        self.operands = operands

    def get_trees(self, tree):
        rows = tree.tensor.size()[0]
        presence = torch.ones(rows)
        trees = []
        before = sum(map(lambda o: o.size(), tree.type.operands[:self.position]))
        cur = before
        for (pattern, child) in zip(self.operands, tree.children[before:]):
            if child is None:
                # Missing child - pattern is not matched
                return torch.zeros(rows), []
            else:
                (child_presence, child_trees) = pattern.get_trees(child)
                child_presence *= tree.tensor[:, cur]
                cur += 1

                presence *= child_presence
                trees.extend(child_trees)

        return presence, trees

    def __repr__(self) -> str:
        return "ConstructorPattern(" + str(self.position) + ", " + str(self.operands) + ")"


class LitPattern(BasePattern):
    def __init__(self, position):
        self.position = position

    def get_trees(self, tree):
        before = sum(map(lambda o: o.size(), tree.type.operands[:self.position]))

        presence = tree.tensor[:, before]
        return presence, []

    def __repr__(self):
        return "LitPattern(" + str(self.position) + ")"


class VarPattern(BasePattern):
    def get_trees(self, tree):
        rows = tree.tensor.size()[0]
        return torch.ones(rows), [tree]

    def __repr__(self):
        return "VarPattern()"
