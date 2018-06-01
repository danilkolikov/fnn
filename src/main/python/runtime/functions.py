import torch
from torch.autograd import Function
from torch.nn.functional import softmax, sigmoid

from .types import LitSpec, ProdSpec


class TypedSigmoid(Function):

    @staticmethod
    def forward(ctx, data_type, input):
        input_softmax = softmax(input, dim=1).detach()
        input_sigmoid = sigmoid(input).detach()

        to_cat = []
        cur = 0
        for spec in data_type.operands:
            if isinstance(spec, LitSpec):
                prod_presence = input_softmax[:, cur]
                presence = prod_presence * input_sigmoid[:, cur]
                to_cat.append(presence)
                cur += 1
                continue
            if isinstance(spec, ProdSpec):
                prod_size = len(spec.operands)
                prod_presence = input_softmax.narrow(1, cur, prod_size).sum(1)
                for _ in spec.operands:
                    presence = prod_presence * input_sigmoid[:, cur]
                    to_cat.append(presence)
                    cur += 1
                continue
            raise ValueError('Unknkown type spec: ' + str(spec))
        result = torch.stack(to_cat, 1)

        ctx.type = data_type
        ctx.save_for_backward(result, input_softmax, input_sigmoid)
        return result

    @staticmethod
    def backward(ctx, grad_output):
        forward_result, softmax_result, sigmoid_result = ctx.saved_tensors

        samples = forward_result.size()[0]
        type_size = ctx.type.size()

        output_column = forward_result.view(samples, type_size, 1)
        sigma_column = sigmoid_result.view(samples, type_size, 1)
        softmax_row = softmax_result.view(samples, -1, type_size)
        prod_delta = torch.eye(type_size, dtype=forward_result.dtype)
        prod_grad = output_column * (prod_delta * (1 - sigma_column) - softmax_row)

        sigma_delta = torch.zeros(type_size, type_size, dtype=forward_result.dtype)
        cur = 0
        for spec in ctx.type.operands:
            if isinstance(spec, LitSpec):
                sigma_delta[cur, cur] = 1
                cur += 1
                continue
            if isinstance(spec, ProdSpec):
                prod_size = spec.size()
                old_cur = cur
                cur += prod_size
                sigma_delta[old_cur:cur, old_cur:cur] = 1
                continue
            raise ValueError('Unexpected TypeSpec: ' + str(spec))
        sigma_grad = sigma_delta * sigma_column * softmax_row
        result = prod_grad + sigma_grad
        return None, grad_output.unsqueeze(1).matmul(result).squeeze(1)


typedSigmoid = TypedSigmoid.apply

