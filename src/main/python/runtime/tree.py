import torch

from .types import TypeSpec, LitSpec, ProdSpec, create_empty_type, create_tuple_type


class TensorTree:
    """
    Generalised version of tensor. As like as tensor, it represents an object of some data type,
    but also stores information about it's structure. Every layer of tree stores tensor, which
    values represent one layer of data structure, type of the layer and links to next layers of
    the structure.
    """

    def __init__(self, tensor, data_type, children):
        if len(children) != tensor.size()[-1]:
            raise ValueError('Mismatching sizes of children array and tensor, '
                             + str(tensor) + '  and ' + str(children))
        self.tensor = tensor
        self.type = data_type
        self.children = children

    def __mul__(self, other):
        """
        Multiply this tree by a number

        :param other: Number, Variable or Tensor
        :return: TensorTree
        """
        new_tensor = self.tensor * other
        new_children = list(map(lambda t: t * other if t is not None else None, self.children))
        return TensorTree(
            new_tensor,
            self.type,
            new_children
        )

    def __add__(self, other):
        """
        Add other tree to this one

        :param other: TensorTree
        :return: TensorTree
        """
        if not isinstance(other, TensorTree):
            raise NotImplemented

        # Add tensors on this layer
        new_tensor = self.tensor + other.tensor

        # Merge child layers and preserve type information
        new_children = []
        new_operands = []
        children_iter = iter(self.children)
        other_iter = iter(other.children)

        for (self_op, other_op) in zip(self.type.operands, other.type.operands):
            if isinstance(self_op, LitSpec) and isinstance(other_op, LitSpec):
                assert next(children_iter) is None
                assert next(other_iter) is None
                # Don't add children, because they're missing
                new_operands.append(self_op)
                new_children.append(None)
                continue

            if isinstance(self_op, ProdSpec) and isinstance(other_op, ProdSpec):
                prod_operands = []
                for (self_prod_op, other_prod_op) in zip(self_op.operands, other_op.operands):
                    self_child = next(children_iter, None)
                    other_child = next(other_iter, None)
                    if self_child is None and other_child is None:
                        # Both children are missing - add None
                        prod_operands.append(self_prod_op)
                        new_children.append(None)
                        continue
                    if self_child is not None and other_child is None:
                        # Child of self is present - save it
                        prod_operands.append(self_prod_op)
                        new_children.append(self_child)
                        continue
                    if self_child is None and other_child is not None:
                        # Child of other is present - save it
                        prod_operands.append(other_prod_op)
                        new_children.append(other_child)
                        continue
                    if self_child is not None and other_child is not None:
                        # Both children exist - should add them
                        tree_sum = self_child + other_child
                        prod_operands.append(tree_sum.type)
                        new_children.append(tree_sum)
                new_operands.append(ProdSpec(prod_operands))
                continue

            raise ValueError('Unexpected combination of type operands: '
                             + str(self_op) + ' and ' + str(other_op))

        new_type = TypeSpec(new_operands)
        return TensorTree(new_tensor, new_type, new_children)

    def presence(self):
        """
        Returns `presence` of an object defined by type in the value of TensorTree
        :return: Tensor representing presence
        """
        rows = self.tensor.size()[0]
        result = torch.zeros(rows)
        cur = 0
        for operand in self.type.operands:
            if isinstance(operand, LitSpec):
                result += self.tensor[:, cur]
                cur += 1
            if isinstance(operand, ProdSpec):
                prod_result = torch.ones(rows)
                for _ in operand.operands:
                    prod_result *= self.tensor[:, cur]
                    cur += 1
                result += prod_result
        return result

    def flatten(self):
        """
        Flattens all tensors and erases type information

        :return: Flat tensor
        """
        to_cat = []
        pos = 0
        rows = self.tensor.size()[0]
        for operand in self.type.operands:
            if isinstance(operand, LitSpec):
                to_cat.append(self.tensor[:, pos].view(rows, 1))
                pos += 1
            if isinstance(operand, ProdSpec):
                for _ in operand.operands:
                    child = self.children[pos]
                    pos += 1
                    if child is None:
                        to_cat.append(torch.zeros(rows, 1))
                    else:
                        to_cat.append(child.flatten())
        return torch.cat(to_cat, 1)

    def select_rows(self, mask):
        if self.tensor.size()[0] == 0:
            # Tree is empty - can't select anything
            return self
        rows = mask.sum().item()
        columns = self.tensor.size()[1]
        new_tensor = self.tensor[mask].view(rows, columns)
        children = [t.select_rows(mask) if t is not None else None for t in self.children]
        return TensorTree(new_tensor, self.type, children)

    def mmul(self, weights, bias):
        """
        Matrix multiplication. Multiplies tree by a matrix and adds bias. Multiplies
        child layers by constants and adds them

        :param weights: Tensor with weights
        :param bias: Tensor with biases
        :return: TensorTree
        """

        raise NotImplemented

    def __repr__(self):
        return 'Tree with type ' + str(self.type) + ', content ' + str(self.tensor) + ' and children: ' \
            + str(self.children)


def empty_tree():
    return TensorTree(torch.tensor([]), create_empty_type(), [])


def stack(trees):
    """
    Concatenates TreeTensors of the same type along 0th direction.

    :param trees: List of TreeTensors
    :return: Merged TreeTensor
    """
    if len(trees) == 0:
        return empty_tree()
    if len(trees) == 1:
        return trees[0]

    # Stack values of this layer
    new_tensor = torch.cat(list(map(lambda t: t.tensor, trees)), 0)
    # Stack values of next layers
    new_operands = []
    new_children = []
    cur = 0
    for operand in trees[0].type.operands:
        if isinstance(operand, LitSpec):
            new_operands.append(operand)
            new_children.append(None)
            cur += 1
            continue
        if isinstance(operand, ProdSpec):
            prod_operands = []
            for prod_operand in operand.operands:
                next_trees = list(map(lambda t: t.children[cur], trees))
                not_none = next(filter(lambda t: t is not None, next_trees), None)
                if not_none is None:
                    # This child is missing in all trees
                    prod_operands.append(prod_operand)
                    new_children.append(None)
                else:
                    # Some trees contain this child
                    to_stack = []
                    child_size = len(not_none.children)
                    for (pos, tree) in enumerate(next_trees):
                        if tree is not None:
                            to_stack.append(tree)
                        else:
                            # Add fake tree with zero elements
                            tree_size = trees[pos].tensor.size()[0]
                            to_stack.append(TensorTree(
                                torch.zeros(tree_size, child_size),
                                not_none.type,
                                [None for _ in range(child_size)]
                            ))
                    stacked = stack(to_stack)
                    prod_operands.append(stacked.type)
                    new_children.append(stacked)
                cur += 1
            new_operands.append(ProdSpec(prod_operands))
            continue
        raise ValueError('Unexpected type spec: ' + str(operand))

    return TensorTree(new_tensor, TypeSpec(new_operands), new_children)


def make_tuple(operands):
    """
    Creates tuple of operands

    :param operands: List of TensorTrees
    :return: TensorTree representing tuple
    """
    if len(operands) == 0:
        return TensorTree(torch.zeros(0), TypeSpec([ProdSpec([])]), [])
    new_tensor = torch.stack(list(map(lambda t: t.presence(), operands)), dim=1)
    new_type = create_tuple_type(list(map(lambda t: t.type, operands)))
    new_children = operands
    return TensorTree(new_tensor, new_type, new_children)
