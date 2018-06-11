import torch
from torch.nn import Parameter

from .base import FunctionalModule
from ..data import DataPointer
from ..functions import structuredSigmoid
from ..trees import SumTree, ProdTree, OperatorTree
from ..types import TypeSpec, LitSpec, ProdSpec, VarSpec, ExtSpec, create_tuple_type, create_unit_type
from ..errors import UnexpectedTypeSpec, UnknownType


class TrainableLayer(FunctionalModule):
    """
    Layer of the network that can be fitted to data.
    """

    def __init__(self, defined_types, arguments, to_type, from_depth=1, to_depth=1):
        super().__init__()
        self.pointer = DataPointer.start    # Trainable networks only use specified arguments
        self.arguments = arguments

        self.from_depth = from_depth * 2   # Every layer of definition of type is unwrapped
        self.to_depth = to_depth * 2        # Into two layers of TensorTree

        # Wrap arguments and results to tuples to support polymorphism
        self.from_type = create_tuple_type(self.arguments)
        self.to_type = create_tuple_type([to_type])

        # Create parameters
        self.weights = _create_weights(
            defined_types, self.from_type, self.to_type, self.from_depth, self.to_depth
        )
        self.bias = _create_bias(defined_types, self.to_type, self.to_depth)

        # Register parameters
        self._register_operator_parameters([], self.weights)
        self._register_tree_parameters('', [], self.bias)

    def forward(self, data_bag):
        linear = self.weights.typed_tree_mul(data_bag.data) + self.bias
        # Result of linear combination is contained in the 0-th child
        child = linear.children[0].children[0]
        result = child.apply_structured_activation(structuredSigmoid)
        # print('params: ', self.weights, '\n', '\nlinear: ', linear, '\nresult: ', result)
        return result

    def _register_operator_parameters(self, path, operator):
        self._register_tree_parameters(str(path) + '_w', [], operator.tree)
        for (i, child) in enumerate(operator.children):
            if child is not None:
                self._register_operator_parameters([*path, i], child)

    def _register_tree_parameters(self, prefix, path, tree):
        if tree.tensor.requires_grad:
            self.register_parameter(prefix + '_' + str(path), tree.tensor)
        for (i, child) in enumerate(tree.children):
            if child is not None:
                self._register_tree_parameters(prefix, [*path, i], child)

    @staticmethod
    def bind_defined_types(defined_types):
        def constructor(arguments, to_type, from_depth=1, to_depth=1):
            return TrainableLayer(defined_types, arguments, to_type, from_depth, to_depth)
        return constructor


def _create_weights(defined_types, from_type, to_type, from_depth, to_depth):
    """
    Creates trainable OperatorTree and fills it with random values
    """

    def _create_tensors(type_params, this_from_type, this_to_type, from_size, to_size):
        weights = torch.randn(from_size, to_size)
        mask, children = _create_weight_mask(type_params, this_from_type, this_to_type, from_size, to_size)
        # Updated parameters only if we have Sum->Sum or Prod->Prod layers
        need_grad = type(this_from_type) == type(this_to_type)
        tensor = weights * mask
        if need_grad:
            result = Parameter(tensor, requires_grad=True)
        else:
            result = tensor
        # TODO: Remove this crutch for literals
        result.children = children
        return result

    return _build_operator(
        defined_types, {}, from_type, to_type, from_depth, to_depth, _create_tensors
    )


def _create_bias(defined_types, to_type, to_depth):
    """
    Creates trainable TensorTree and fills it with random values
    """
    def _create_random_tensor(type_params, this_from_type, this_to_type, from_size, to_size):
        return Parameter(torch.randn(from_size, to_size), requires_grad=True)

    return _build_tree(
        defined_types, {}, create_unit_type(), to_type, to_depth, _create_random_tensor
    )


def _create_weight_mask(type_params, from_type, to_type, from_size, to_size):
    # Let's set values of the mask using axioms of logic:
    # (a -> T) holds for every a, so we can create a literal using any object - set mask to 1
    # (a -> a) holds for every a, so we can create an object using object of it's type - set mask to 1
    # (a -> b) is false for a != b, so we can't create an object of one type using object of other type
    #   - set mask to 0
    if isinstance(from_type, TypeSpec):
        if isinstance(to_type, TypeSpec):
            # Both are TypeSpecs
            mask = torch.zeros(from_size, to_size)
            for (column, to_operand) in enumerate(to_type.operands):
                if isinstance(to_operand, LitSpec):
                    mask[:, column] = 1
                    continue
            return mask, None
        if isinstance(to_type, ProdSpec):
            # We can't create a product from a sum
            return torch.zeros(from_size, to_size), None
        raise UnexpectedTypeSpec(to_type)
    if isinstance(from_type, ProdSpec):
        if isinstance(to_type, TypeSpec):
            # We can't create a sum from a product
            return torch.zeros(from_size, to_size), None
        if isinstance(to_type, ProdSpec):
            # Both are products
            mask = torch.zeros(from_size, to_size)
            children = [[False for _ in range(to_size)] for _ in range(from_size)]
            for (row, from_operand) in enumerate(from_type.operands):
                for (column, to_operand) in enumerate(to_type.operands):
                    while isinstance(from_operand, VarSpec) and from_operand.name in type_params:
                        from_operand = type_params[from_operand.name]
                    while isinstance(to_operand, VarSpec) and to_operand.name in type_params:
                        to_operand = type_params[to_operand.name]
                    if from_operand == to_operand:
                        mask[row, column] = 1
                        children[row][column] = True
            return mask, children
        raise UnexpectedTypeSpec(to_type)
    raise UnexpectedTypeSpec(from_type)


def _build_operator(defined_types, type_params, from_type, to_type, from_depth, to_depth, tensor_builder):
    """
    Builds OperatorTree using specified type information, depth and builder of tensor for one layer

    :param from_type: Type of structure of OperatorTree
    :param to_type: Type of TreeTensors in OperatorTree
    :param from_depth: Depth of OperatorTree
    :param to_depth: Depth of TreeTensors in OperatorTree
    :param tensor_builder: Builder of tensors
    :return: OperatorTree
    """

    from_size = len(from_type.operands)
    weight = _build_tree(
        defined_types, {}, from_type, to_type, to_depth, tensor_builder
    )
    if isinstance(from_type, TypeSpec):
        if from_depth == 0:
            children = [None] * from_size
        else:
            children = []
            for operand in from_type.operands:
                if isinstance(operand, LitSpec):
                    children.append(None)
                    continue
                if isinstance(operand, ProdSpec):
                    child = _build_operator(
                        defined_types, type_params, operand, to_type, from_depth - 1, to_depth, tensor_builder
                    )
                    children.append(child)
                    continue
                raise UnexpectedTypeSpec(operand)
        return OperatorTree(weight, children)
    if isinstance(from_type, ProdSpec):
        if from_depth == 0:
            children = [None] * from_size
        else:
            children = []
            for operand in from_type.operands:
                while isinstance(operand, VarSpec) and operand.name in type_params:
                    operand = type_params[operand.name]
                if isinstance(operand, VarSpec):
                    children.append(None)
                    continue
                if isinstance(operand, ExtSpec):
                    if operand.name not in defined_types:
                        raise UnknownType(operand.name)
                    next_type = defined_types[operand.name]
                    next_params = {**type_params, **operand.args}
                    child = _build_operator(
                        defined_types, next_params, next_type, to_type, from_depth - 1, to_depth, tensor_builder
                    )
                    children.append(child)
                    continue
                raise UnexpectedTypeSpec(operand)
        return OperatorTree(weight, children)
    raise UnexpectedTypeSpec(from_type)


def _build_tree(defined_types, type_params, from_type, to_type, to_depth, tensor_builder):
    """
    Builds TensorTree using specified type information and builder of tensor

    :param from_type: Type of structure of this TreeTensor belongs to
    :param to_type: Type of TreeTensor
    :param to_depth: Depth of TreeTensor
    :param tensor_builder: Builder of tensor
    :return: TreeTensor
    """

    from_size = len(from_type.operands)
    to_size = len(to_type.operands)
    tensor = tensor_builder(type_params, from_type, to_type, from_size, to_size)

    if isinstance(to_type, TypeSpec):
        if to_depth == 0:
            children = [None] * to_size
        else:
            children = []
            for operand in to_type.operands:
                if isinstance(operand, LitSpec):
                    children.append(None)
                    continue
                if isinstance(operand, ProdSpec):
                    child = _build_tree(
                        defined_types, type_params, from_type, operand, to_depth - 1, tensor_builder
                    )
                    children.append(child)
                    continue
                raise UnexpectedTypeSpec(operand)
        return SumTree(tensor, children)
    if isinstance(to_type, ProdSpec):
        if to_depth == 0:
            children = [None] * to_size
        else:
            children = []
            for operand in to_type.operands:
                while isinstance(operand, VarSpec) and operand.name in type_params:
                    operand = type_params[operand.name]
                if isinstance(operand, VarSpec):
                    children.append(None)
                    continue
                if isinstance(operand, ExtSpec):
                    if operand.name not in defined_types:
                        raise UnknownType(operand.name)
                    next_type = defined_types[operand.name]
                    next_params = {**type_params, **operand.args}
                    child = _build_tree(
                        defined_types, next_params, from_type, next_type, to_depth - 1, tensor_builder
                    )
                    children.append(child)
                    continue
                raise UnexpectedTypeSpec(operand)
        return ProdTree(tensor, children)
    raise UnexpectedTypeSpec(to_type)
