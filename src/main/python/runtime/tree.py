import torch

from .types import TypeSpec, LitSpec, ProdSpec, VarSpec, create_empty_type, create_tuple_type


class TensorTree:
    """
    Generalised version of tensor. As like as tensor, it represents an object of some data type,
    but also stores information about it's structure. Every layer of tree stores tensor, which
    values represent one layer of data structure, type of the layer and links to next layers of
    the structure.
    """

    def __init__(self, tensor, data_type, children):
        self.tensor = tensor
        self.type = data_type
        self.children = children

    def to(self, device):
        new_tensor = self.tensor.to(device)
        new_type = self.type
        new_children = [child.to(device) if child is not None else None for child in self.children]
        return TensorTree(new_tensor, new_type, new_children)

    def instantiate(self, **kwargs):
        return TensorTree(
            self.tensor,
            self.type.instantiate(**kwargs),
            [child.instantiate(**kwargs) if child is not None else None for child in self.children]
        )

    def rows(self):
        return self.tensor.size()[0]

    def restore_type(self):
        """
        Restore type of tree by it's children

        :return: Tree with the same structure and restored type
        """
        j = 0
        operands = []
        for operand in self.type.operands:
            if isinstance(operand, LitSpec):
                operands.append(operand)
            if isinstance(operand, ProdSpec):
                prod_operands = []
                for prod_operand in operand.operands:
                    if isinstance(prod_operand, VarSpec):
                        operand_type = prod_operand
                    else:
                        operand_type = self.children[j].type if self.children[j] is not None else prod_operand
                    prod_operands.append(operand_type)
                    j += 1
                operands.append(ProdSpec(prod_operands))
            j += 1

        new_type = TypeSpec(operands)
        return TensorTree(self.tensor, new_type, self.children)

    def cmul(self, constant):
        """
        Multiply this tree by a constant value

        :param constant: Constant tensor that supports broadcasting
        :return: TensorTree
        """

        new_tensor = constant * self.tensor
        new_type = self.type
        new_children = [child.cmul(constant) if child is not None else None for child in self.children]

        return TensorTree(new_tensor, new_type, new_children)

    def cadd(self, constant):
        """
        Add a constant value to this tree

        :param constant: Constant tensor that supports broadcasting
        :return: TensorTree
        """

        new_tensor = self.tensor + constant
        new_type = self.type
        new_children = [child.cadd(constant) if child is not None else None for child in self.children]

        return TensorTree(new_tensor, new_type, new_children)

    def matmul(self, matrix, res_type):
        """
        Multiply tensor on the top layer of this TensorTree by a matrix (M)

        Then it does action similar to matrix multiplication, but on children of the TensorTree. It
        multiplies the i-th children of this tensor by a constant `M[i, j]` and adds it to the j-th
        children of the result.

        Basically, it's a matrix multiplication, generalised to TreeTensors

        :param matrix: Tensor with size [n, m]
        :param res_type: Type of the result of multiplication
        :return: TreeTensor
        """

        # Multiply tensor by a matrix
        new_tensor = self.tensor.matmul(matrix)

        # Multiply children
        new_children = [None] * res_type.size()
        for (i, child) in enumerate(self.children):
            for (j, spec) in enumerate(res_type.specs()):
                element = matrix[i, j]
                if element.item() == 0 or child is None:
                    # Skip children that are multiplied by 0
                    continue
                if isinstance(spec, LitSpec):
                    continue

                multiplied = child.cmul(element)
                if new_children[j] is None:
                    new_children[j] = multiplied
                else:
                    new_children[j] += multiplied

        return TensorTree(new_tensor, res_type, new_children).restore_type()

    def _pointwise_op(self, tensor_op, constant_op, other):
        """
        Apply point-wise operation to this and other tensor, e.g. point-wise addition

        :param tensor_op: Tensor operation on content of tree
        :param constant_op: Constant operation
        :param other: Number, Variable, Tensor or TensorTree
        :return: TensorTree
        """
        if not isinstance(other, TensorTree):
            raise NotImplemented

        new_tensor = tensor_op(self.tensor, other.tensor)

        new_children = []
        new_operands = []

        children_iter = iter(enumerate(zip(self.children, other.children)))

        for (self_op, other_op) in zip(self.type.operands, other.type.operands):
            if isinstance(self_op, LitSpec) and isinstance(other_op, LitSpec):
                next(children_iter)
                new_operands.append(self_op)
                new_children.append(None)
                continue
            if isinstance(self_op, ProdSpec) and isinstance(other_op, ProdSpec):
                next_operands = []
                for (self_prod_op, other_prod_op) in zip(self_op.operands, other_op.operands):
                    (cur, (self_child, other_child)) = next(children_iter)
                    if self_child is None:
                        if other_child is None:
                            # Both are missing
                            next_child = None
                        else:
                            # Other child is present
                            self_constant = self.tensor[:, cur].view(self.rows(), 1)
                            next_child = constant_op(other_child, self_constant)
                    else:
                        if other_child is None:
                            # Self child is present
                            other_constant = other.tensor[:, cur].view(other.rows(), 1)
                            next_child = constant_op(self_child, other_constant)
                        else:
                            # Both are present
                            next_child = self_child._pointwise_op(tensor_op, constant_op, other_child)

                    next_op = next_child.type if next_child is not None else self_prod_op
                    next_operands.append(next_op)
                    new_children.append(next_child)
                new_operand = ProdSpec(next_operands)
                new_operands.append(new_operand)
                continue
            raise ValueError('Unexpected combination of type operands: '
                             + str(self_op) + ' and ' + str(other_op))

        new_type = TypeSpec(new_operands)
        return TensorTree(new_tensor, new_type, new_children)

    def __mul__(self, other):
        """
        Point-wise multiply this tree by a number, Tensor or TensorTree

        :param other: Number, Variable, Tensor or TensorTree
        :return: TensorTree
        """
        return self._pointwise_op(lambda a, b: a * b, lambda t, c: t.cmul(c), other)

    def __add__(self, other):
        """
        Point-wise add other tree to this one

        :param other: float, tensor or TensorTree
        :return: TensorTree
        """
        return self._pointwise_op(lambda a, b: a + b, lambda t, c: t.cadd(c), other)

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
                    prod_result = prod_result * self.tensor[:, cur]
                    cur += 1
                result += prod_result
        return result

    def flat_width(self, with_presence=False):
        """
        Number of columns in flattened tensor
        :return: Number
        """
        if isinstance(self.type, VarSpec):
            return 0
        res = 0
        for (index, spec) in enumerate(self.type.specs()):
            if self.children[index] is None:
                res += 1
            else:
                if with_presence:
                    res += 1
                res += self.children[index].flat_width(with_presence)
        return res

    def flatten(self, like_tree=None, with_presence=False):
        """
        Flattens all tensors and erases type information

        :return: Flat tensor
        """
        to_cat = []
        pos = 0
        rows = self.tensor.size()[0]
        if isinstance(self.type, VarSpec):
            if like_tree is not None:
                columns = like_tree.flat_width()
            else:
                columns = 0
            return torch.zeros(rows, columns)
        for operand in self.type.operands:
            if isinstance(operand, LitSpec):
                to_cat.append(self.tensor[:, pos].view(rows, 1))
                pos += 1
            if isinstance(operand, ProdSpec):
                for _ in operand.operands:
                    child = self.children[pos]
                    like_child = None if like_tree is None else like_tree.children[pos]

                    if child is None:
                        if like_child is not None:
                            if with_presence:
                                to_cat.append(self.tensor[:, pos].view(rows, 1))
                            zeros_count = like_child.flat_width(with_presence)
                            to_cat.append(torch.zeros(rows, zeros_count))
                        else:
                            to_cat.append(self.tensor[:, pos].view(rows, 1))
                    else:
                        if with_presence:
                            to_cat.append(self.tensor[:, pos].view(rows, 1))
                        to_cat.append(child.flatten(like_child, with_presence))
                    pos += 1

        res = torch.cat(to_cat, 1)
        return res

    def select_rows(self, mask):
        if self.tensor.size()[0] == 0:
            # Tree is empty - can't select anything
            return self
        rows = mask.sum().item()
        columns = self.tensor.size()[1]
        new_tensor = self.tensor[mask].view(rows, columns)
        children = [t.select_rows(mask) if t is not None else None for t in self.children]
        return TensorTree(new_tensor, self.type, children)

    def select_row(self, row):
        if self.tensor.dim() == 0:
            # Tree is empty - can't select anything
            return self
        if isinstance(self.type, VarSpec) and row != 0:
            # Tensor's type is VarSpec - select 0th row
            return self.select_row(0)
        columns = self.tensor.size()[1]
        new_tensor = self.tensor[row].view(1, columns)
        children = [t.select_row(row) if t is not None else None for t in self.children]
        return TensorTree(new_tensor, self.type, children)

    def tree_mul(self, other):
        """
        Multiplies first level of this tree by the other tree in a way, similar to matrix multiplication.

        :param other: Other tree
        :return: TensorTree
        """
        if not isinstance(other, TensorTree):
            raise NotImplemented

        new_tree = self.matmul(other.tensor, other.type)

        rows = new_tree.rows()
        child_columns = []
        child_children = []
        child_operands = []
        children_iter = enumerate(other.children)
        for spec in other.type.operands:
            if isinstance(spec, LitSpec):
                child_children.append(None)
                child_columns.append(torch.zeros(rows))
                child_operands.append(spec)
                next(children_iter)
                continue
            if isinstance(spec, ProdSpec):
                prod_operands = []
                for prod_operand in spec.operands:
                    (i, other_child) = next(children_iter)
                    if other_child is None:
                        prod_operands.append(prod_operand)
                        child_columns.append(torch.zeros(rows))
                        child_children.append(None)
                    else:
                        multiplied = self.tree_mul(other_child)
                        child_columns.append(multiplied.presence())
                        prod_operands.append(multiplied.type)
                        child_children.append(multiplied)
                child_operands.append(ProdSpec(prod_operands))
                continue
            raise ValueError('Unexpected Type: ' + str(spec))

        child_tensor = torch.stack(child_columns, 1)
        child_type = TypeSpec(child_operands)
        child_tree = TensorTree(child_tensor, child_type, child_children)

        return new_tree + child_tree

    def apply_activation(self, func):
        """
        Applies arbitrary activation functions to all tensors of this TensorTree

        :param func: Activation function
        :return: TreeTensor
        """

        new_tensor = func(self.tensor)
        new_type = self.type
        new_children = [None if child is None else child.apply_activation(func) for child in self.children]
        return TensorTree(new_tensor, new_type, new_children)

    def apply_typed_activation(self, func):
        """
        Applies typed activation function to all tensors of this TensorTree

        :param func: Activation function
        :return: TreeTensor
        """

        new_tensor = func(self.type, self.tensor)
        new_type = self.type
        new_children = [None if child is None else child.apply_typed_activation(func) for child in self.children]
        return TensorTree(new_tensor, new_type, new_children)

    def __repr__(self):
        return 'Tree with content ' + str(self.tensor) + ' and children: ' \
               + str(self.children)


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

    def __repr__(self):
        return 'OperatorTree(' + str(self.tree) + ', ' + str(self.children) + ')'


def empty_tree(data_type=None, size=1):
    if data_type is None:
        return TensorTree(torch.tensor([]), create_empty_type(), [])
    else:
        type_size = data_type.size()
        return TensorTree(torch.zeros(size, type_size), data_type, [None] * type_size)


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
    not_variable = next(filter(lambda t: not isinstance(t.type, VarSpec), trees), None)
    if not_variable is None:
        # Every tree is variable, children are empty
        return TensorTree(new_tensor, trees[0].type, new_children)

    for operand in not_variable.type.operands:
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
