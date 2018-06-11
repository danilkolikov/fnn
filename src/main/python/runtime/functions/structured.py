from torch.autograd import Function
from torch.nn import Sigmoid, Softmax


class StructuredFunction:
    """
    Two activation functions, one represents restrictions of Sum Type (results should sum to 1),
    other - restrictions of Product Type (results are in [0, 1] and independent of each other)
    """

    def __init__(self, sum, prod):
        self.sum = sum
        self.prod = prod


class Linear(Function):
    """
    Linear activation function. Doesn't do any transformation of data
    """

    @staticmethod
    def forward(ctx, input):
        return input

    @staticmethod
    def backward(ctx, grad_outputs):
        return grad_outputs


linear = Linear.apply

structuredLinear = StructuredFunction(linear, linear)

structuredSigmoid = StructuredFunction(Softmax(dim=1), Sigmoid())
