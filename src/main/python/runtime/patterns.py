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
        rows = tree.rows()
        # Presence of this constructor
        presence = tree.tensor[:, self.position]
        product = tree.children[self.position]
        trees = []
        if product is None:
            # Pattern is not matched
            return None, []
        # Presence of children
        for (cur, (pattern, child)) in enumerate(zip(self.operands, product.children)):
            if child is None:
                # Missing child - pattern is not matched
                return None, []
            else:
                scale = product.tensor[:, cur]
                if isinstance(pattern, LitPattern):
                    child_presence, _ = pattern.get_trees(child)
                    child_presence = child_presence * scale
                else:
                    scale = scale.view(rows, 1)
                    (child_presence, child_trees) = pattern.get_trees(child)
                    if child_presence is None:
                        # Child is not matched
                        return None, []
                    scaled_children = [c.cmul(scale) for c in child_trees]
                    trees.extend(scaled_children)
                presence = presence * child_presence

        return presence, trees

    def __repr__(self) -> str:
        return "ConstructorPattern(" + str(self.position) + ", " + str(self.operands) + ")"


class LitPattern(BasePattern):
    def __init__(self, position):
        self.position = position

    def get_trees(self, tree):
        presence = tree.tensor[:, self.position]
        return presence, []

    def __repr__(self):
        return "LitPattern(" + str(self.position) + ")"


class VarPattern(BasePattern):
    def get_trees(self, tree):
        return torch.ones(tree.rows()), [tree]

    def __repr__(self):
        return "VarPattern()"
