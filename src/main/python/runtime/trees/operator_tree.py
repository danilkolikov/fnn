import torch
from .tensor_tree import TensorTree, SumTree


class OperatorTree:
    """
    Tree that contains TensorTree in every node. It's equivalent to a matrix operator
    """

    EPS = 1e-4

    def __init__(self, tree, children):
        self.tree = tree
        self.children = children

    def instantiate(self, **kwargs):
        return OperatorTree(
            self.tree.instantiate(**kwargs),
            [child.instantiate(**kwargs) if child is not None else None for child in self.children]
        )

    def pointwise_mul(self, other):
        """
        Pointwise multiplication of OperatorTree-s of the same structure

        :param other:  OperatorTree
        :return: OperatorTree
        """

        if not isinstance(other, OperatorTree):
            raise NotImplemented

        if len(self.children) != len(other.children):
            raise ValueError(
                'Mismatching sizes of children: ' + str(len(self.children)) + ' and ' + str(len(other.children))
            )

        new_tree = self.tree * other.tree
        new_children = []

        for (self_child, other_child) in zip(self.children, other.children):
            if self_child is None and other_child is None:
                new_children.append(None)
                continue

            if self_child is not None and other_child is not None:
                new_child = self_child.pointwise_mul(other_child)
                new_children.append(new_child)
                continue

            raise ValueError('Unexpected combination of OperatorTrees: ' + str(self_child) + ' and ' + str(other_child))
        return OperatorTree(new_tree, new_children)

    def tree_mul(self, tree):
        """
        Multiplies this OperatorTree by a TensorTree in a way, similar to matrix multiplication.

        :param tree: TensorTree
        :return: TensorTree
        """
        if not isinstance(tree, TensorTree):
            raise NotImplemented

        result = tree.tree_mul(self.tree)

        for (index, (operator_child, tree_child)) in enumerate(zip(self.children, tree.children)):
            if operator_child is not None and tree_child is not None:
                child_presence = tree.tensor[:, index].view(tree.rows(), 1)
                if (child_presence < OperatorTree.EPS).all():
                    # Skip child if all values are lower than EPS
                    continue
                result = result + operator_child.tree_mul(tree_child).cmul(child_presence)
        return result

    def typed_tree_mul(self, tree):
        """
        Multiplies this OperatorTree by a TensorTree in a way, similar to matrix multiplication.
        Other tree consists of SumTrees and ProdTrees

        :param tree: SumTree
        :return: TensorTree
        """
        if not isinstance(tree, SumTree):
            raise NotImplemented

        result = tree.typed_tree_mul(self.tree)

        for (operator_child, tree_child) in zip(self.children, tree.children):
            if operator_child is None or tree_child is None:
                continue
            # Skip first layer of OperatorTree as it's a Sum
            sum_columns = []
            sum_children = []
            for prod_op_child in operator_child.tree.children:
                if prod_op_child is None:
                    continue
                multiplied = tree_child.typed_tree_mul(prod_op_child)
                sum_columns.append(multiplied.presence())
                sum_children.append(multiplied)
            sum_tensor = torch.stack(sum_columns, 1)
            sum_tree = SumTree(sum_tensor, sum_children)
            result = result + sum_tree
            # Multiply children
            for (prod_op_op, tree_child_op) in zip(operator_child.children, tree_child.children):
                if prod_op_op is None or tree_child_op is None:
                    continue
                result = result + prod_op_op.typed_tree_mul(tree_child_op)
        return result

    def __repr__(self):
        return 'OperatorTree(' + str(self.tree) + ', ' + str(self.children) + ')'
