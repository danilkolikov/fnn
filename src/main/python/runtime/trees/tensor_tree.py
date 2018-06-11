from abc import abstractmethod

import torch


class TensorTree:
    """
    Generalised version of tensor. As like as tensor, it represents an object of some data type,
    but also stores information about it's structure. Every layer of tree stores tensor, which
    values represent one layer of data structure and links to next layers of
    the structure.
    """

    def __init__(self, tensor, children):
        self.tensor = tensor
        self.children = children

    def to(self, device):
        new_tensor = self.tensor.to(device)
        new_children = [child.to(device) if child is not None else None for child in self.children]
        return self.__class__(new_tensor, new_children)

    def type(self, tensor_type):
        new_tensor = self.tensor.type(tensor_type)
        new_children = [child.type(tensor_type) if child is not None else None for child in self.children]
        return self.__class__(new_tensor, new_children)

    def rows(self):
        return self.tensor.size()[0]

    def prune(self, eps=1e-3, multiplier=None):
        if multiplier is None:
            new_tensor = self.tensor
        else:
            new_tensor = self.tensor * multiplier
        new_children = []
        for (pos, child) in enumerate(self.children):
            if child is None:
                new_children.append(None)
                continue
            not_prune = self.tensor.detach()[:, pos] > eps
            if not not_prune.any():
                new_children.append(None)
                continue
            not_prune = not_prune.view(self.rows(), 1).float()
            if multiplier is None:
                new_multiplier = not_prune
            else:
                new_multiplier = multiplier * not_prune
            pruned = child.prune(eps, new_multiplier)
            new_children.append(pruned)
        return self.__class__(new_tensor, new_children)

    @abstractmethod
    def _make_strict_tensor(self, tensor, eps):
        pass

    def strict(self, eps=0.5):
        new_tensor = self._make_strict_tensor(self.tensor, eps)
        new_children = [child.strict(eps) if child is not None else None for child in self.children]
        return self.__class__(new_tensor, new_children)

    def cmul(self, constant):
        """
        Multiply this tree by a constant value

        :param constant: Constant tensor that supports broadcasting
        :return: TensorTree
        """

        new_tensor = constant * self.tensor
        new_children = [child.cmul(constant) if child is not None else None for child in self.children]

        return self.__class__(new_tensor, new_children)

    def cadd(self, constant):
        """
        Add a constant value to this tree

        :param constant: Constant tensor that supports broadcasting
        :return: TensorTree
        """

        new_tensor = self.tensor + constant
        new_children = [child.cadd(constant) if child is not None else None for child in self.children]

        return self.__class__(new_tensor, new_children)

    @abstractmethod
    def matmul(self, matrix, tree_class):
        """
        Multiply tensor on the top layer of this TensorTree by a matrix (M)

        Then it does action similar to matrix multiplication, but on children of the TensorTree. It
        multiplies the i-th children of this tensor by a constant `M[i, j]` and adds it to the j-th
        children of the result.

        Basically, it's a matrix multiplication, generalised to TreeTensors

        :param matrix: Tensor with size [n, m], should contain field `children`: two-dimensional list of bool,
            childnren[i, j] = True if we should add i-th children of this tree to the j-th child of result
        :return: TreeTensor
        """
        pass

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

        for (cur, (self_child, other_child)) in enumerate(zip(self.children, other.children)):
            if self_child is None:
                if other_child is None:
                    # Both children are missing
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

            new_children.append(next_child)

        return self.__class__(new_tensor, new_children)

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

    @abstractmethod
    def presence(self):
        pass

    def flat_width(self):
        """
        Number of columns in flattened tensor
        :return: Number
        """
        res = 0
        for child in self.children:
            if child is None:
                res += 1
            else:
                res += child.flat_width()
        return res

    def flatten(self, like_tree=None):
        """
        Flattens all tensors and erases type information

        :return: Flat tensor
        """
        to_cat = []
        rows = self.tensor.size()[0]
        for (pos, child) in enumerate(self.children):
            like_child = None if like_tree is None else like_tree.children[pos]

            if child is None:
                if like_child is not None:
                    zeros_count = like_child.flat_width()
                    flat = torch.zeros(rows, zeros_count)
                else:
                    flat = self.tensor[:, pos].view(rows, 1)
            else:
                multiplied = child.cmul(self.tensor[:, pos].view(rows, 1))
                flat = multiplied.flatten(like_child)
            to_cat.append(flat)

        res = torch.cat(to_cat, 1)
        return res

    def tree_mul(self, other):
        """
        Multiplies first level of this tree by the other tree in a way, similar to matrix multiplication.

        :param other: Other tree
        :return: TensorTree
        """
        if not isinstance(other, TensorTree):
            raise NotImplemented

        new_tree = self.matmul(other.tensor, other.__class__)

        rows = new_tree.rows()
        child_columns = []
        child_children = []

        for other_child in other.children:
            if other_child is None:
                child_columns.append(torch.zeros(rows))
                child_children.append(None)
            else:
                multiplied = self.tree_mul(other_child)
                child_columns.append(multiplied.presence())
                child_children.append(multiplied)

        child_tensor = torch.stack(child_columns, 1)
        child_tree = self.__class__(child_tensor, child_children)

        print(new_tree.flatten(), child_tree.flatten())

        return new_tree + child_tree

    def typed_tree_mul(self, other):
        """
        Multiplication of trees, separated to Sum and Prod layers

        :param other: Other tree
        :return: TensorTree
        """
        assert type(self) == type(other)
        this_layer = self.__class__
        next_layer = SumTree if self.__class__ == ProdTree else ProdTree
        sum_base_tree = self.matmul(other.tensor, this_layer)
        rows = self.rows()

        sum_columns = []
        sum_children = []
        for other_product in other.children:
            if other_product is None:
                sum_columns.append(torch.zeros(rows))
                sum_children.append(None)
                continue

            # Skip one layer and multiply with the second one
            product_columns = []
            product_children = []
            for other_product_child in other_product.children:
                if other_product_child is None:
                    product_columns.append(torch.zeros(rows))
                    product_children.append(None)
                    continue
                multiplied = self.typed_tree_mul(other_product_child)
                product_columns.append(multiplied.presence())
                product_children.append(multiplied)

            product_add_tensor = torch.stack(product_columns, 1)
            product_add = next_layer(product_add_tensor, product_children)

            sum_columns.append(product_add.presence())
            sum_children.append(product_add)

        sum_add_tensor = torch.stack(sum_columns, 1)
        sum_add_tree = this_layer(sum_add_tensor, sum_children)
        result = sum_base_tree + sum_add_tree
        return result

    def apply(self, func):
        new_tensor = func(self.tensor)
        new_children = [None if child is None else child.apply(func) for child in self.children]
        return self.__class__(new_tensor, new_children)

    def select_rows(self, mask):
        return self.apply(lambda tensor: tensor[mask])

    def apply_activation(self, func):
        """
        Applies arbitrary activation functions to all tensors of this TensorTree

        :param func: Activation function
        :return: TreeTensor
        """
        return self.apply(func)

    @abstractmethod
    def _apply_structured_function(self, funcs, tensor):
        pass

    def apply_structured_activation(self, funcs):
        new_tensor = self._apply_structured_function(funcs, self.tensor)
        new_children = [None if child is None else child.apply_structured_activation(funcs) for child in self.children]
        return self.__class__(new_tensor, new_children)

    def __repr__(self):
        return 'Tree with content ' + str(self.tensor) + ' and children: ' \
               + str(self.children)


class SumTree(TensorTree):

    def presence(self):
        return self.tensor.sum(1)

    def _apply_structured_function(self, funcs, tensor):
        return funcs.sum(tensor)

    def _make_strict_tensor(self, tensor, eps):
        res = torch.zeros_like(tensor)
        max_arg = tensor.max(1)[1]
        for (i, j) in enumerate(max_arg):
            res[i, j] = 1
        return res

    def matmul(self, matrix, tree_class):
        # Multiply tensor by a matrix
        new_tensor = self.tensor.mm(matrix)
        _, columns = matrix.size()

        new_children = [None] * columns

        return tree_class(new_tensor, new_children)

    def __repr__(self):
        return 'Sum' + super().__repr__()


class ProdTree(TensorTree):

    EPS = 1e-3

    def presence(self):
        return self.tensor.prod(1)

    def _apply_structured_function(self, funcs, tensor):
        return funcs.prod(tensor)

    def _make_strict_tensor(self, tensor, eps):
        res = torch.zeros_like(tensor)
        res[tensor > eps] = 1
        return res

    def matmul(self, matrix, tree_class):
        new_tensor = self.tensor.mm(matrix)
        _, columns = matrix.size()

        # Multiply children
        new_children = [None] * columns
        for (i, child) in enumerate(self.children):
            if child is None:
                continue
            for j in range(columns):
                element = matrix[i, j]
                if not matrix.children[i][j] or abs(element.item()) < self.EPS:
                    # Skip such children
                    continue

                multiplied = child.cmul(element)
                if new_children[j] is None:
                    new_children[j] = multiplied
                else:
                    new_children[j] += multiplied

        return tree_class(new_tensor, new_children)

    def __repr__(self):
        return 'Prod' + super().__repr__()


def empty_tree():
    return SumTree(torch.tensor([]), [])


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

    first = trees[0]
    # Stack values of this layer
    new_tensor = torch.cat(list(map(lambda t: t.tensor, trees)), 0)
    # Stack values of next layers
    new_children = []

    for cur in range(len(first.children)):
        next_trees = list(map(lambda t: t.children[cur], trees))
        not_none = next(filter(lambda t: t is not None, next_trees), None)
        if not_none is None:
            # This child is missing in all trees
            new_children.append(None)
        else:
            # Some trees contain this child
            to_stack = []
            child_size = len(not_none.children)
            for (pos, tree) in enumerate(next_trees):
                if tree is not None:
                    to_stack.append(tree)
                else:
                    # Add fake tree with equal elements (to support loss function)
                    tree_size = trees[pos].tensor.size()[0]
                    if isinstance(not_none, SumTree):
                        content = torch.ones(tree_size, child_size) * (1.0 / child_size)
                    else:
                        content = torch.ones(tree_size, child_size) * 0.5
                    to_stack.append(not_none.__class__(
                        content,
                        [None for _ in range(child_size)]
                    ))
            stacked = stack(to_stack)
            new_children.append(stacked)

    return first.__class__(new_tensor, new_children)


def make_tuple(operands):
    """
    Creates tuple of operands

    :param operands: List of TensorTrees
    :return: TensorTree representing tuple
    """
    if len(operands) == 0:
        return empty_tree()
    new_tensor = torch.stack([t.presence() for t in operands], dim=1)
    new_children = operands
    product = ProdTree(new_tensor, new_children)
    rows, _ = new_tensor.size()
    sum_data = torch.ones(rows, 1)
    return SumTree(sum_data, [product])
