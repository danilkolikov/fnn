import math

import torch
from torch.autograd import Variable
from torch.nn import Module, Parameter
from torch.nn.functional import sigmoid, linear

from .data import DataPointer
from .modules import FunctionalModule


class TrainablePolyNet(Module):
    """
    Trainable polymorphic network.

    It can be instantiated with arbitrary types and learns behaviour that doesn't depend on types of arguments.

    After each backprop through instances, the method `update_instances` should be called to recompute instance graph
    """

    def __init__(self, arguments, result):
        super().__init__()
        self.arguments = arguments
        self.result = result

        self.weights = [[] for _ in result]
        self.biases = [None for _ in result]
        length = 0

        # Register parameters
        for (res_idx, result_size) in enumerate(result):
            if type(result_size) == str:
                # Type parameters should have size = 1
                result_size = 1

            for (arg_idx, arg_size) in enumerate(arguments):
                if type(arg_size) == str:
                    # Type parameters should have size = 1
                    arg_size = 1
                weight = Parameter(torch.Tensor(result_size, arg_size))
                length += weight.size(1)
                self.weights[res_idx].append(weight)
                self.register_parameter(str(res_idx) + '_' + str(arg_idx) + '_w', weight)

            bias = Parameter(torch.Tensor(result_size))
            self.biases[res_idx] = bias
            self.register_parameter(str(res_idx) + '_b', bias)

        # Init parameters
        stdv = 1. / math.sqrt(length)
        for i in range(len(result)):
            for weight in self.weights[i]:
                weight.data.uniform_(-stdv, stdv)
            self.biases[i].data.uniform_(-stdv, stdv)
        self.instances = []

    def instantiate(self, type_params=None):
        if type_params is None:
            type_params = {}

        weight, bias = self._compute_parameters(type_params)
        self.instances.append({
            'params': type_params,
            'weight': weight,
            'bias': bias
        })

        return TrainablePolyNetInstance(self, len(self.instances) - 1)

    def update_instances(self):
        for instance in self.instances:
            weight, bias = self._compute_parameters(instance['params'])
            instance['weight'] = weight
            instance['bias'] = bias

    def forward(self, instance, data_bag, *rest):
        params = self.instances[instance]
        linear_output = linear(data_bag.data, params['weight'], params['bias'])
        # ToDo: Implement fancy type-based activation function
        return sigmoid(linear_output)

    def _compute_parameters(self, type_params):
        final_biases = []
        final_weights = []
        for (res_size, weights, bias) in zip(self.result, self.weights, self.biases):
            if type(res_size) == str:
                res_size = type_params[res_size]

            instance_weight = []
            for (arg_size, weight) in zip(self.arguments, weights):
                if type(arg_size) == str:
                    arg_size = type_params[arg_size]
                if arg_size > weight.size()[1]:
                    if res_size > weight.size()[0]:
                        # Both argument and result are polymorphic
                        # Weight matrix is an "identity" multiplied by weight tensor
                        this_weight = weight * Variable(torch.eye(res_size, arg_size))
                    else:
                        # Argument is polymorphic, result is algebraic
                        # Repeat raw to fit size of instantiated type
                        this_weight = weight.repeat(1, arg_size)
                else:
                    if res_size > weight.size()[0]:
                        # Argument is algebraic, result is polymorphic
                        # Repeat column to fit size of result type
                        this_weight = weight.repeat(res_size, 1)
                    else:
                        # Both argument and result are algebraic
                        this_weight = weight

                instance_weight.append(this_weight)
            weight = torch.cat(instance_weight, 1)
            if res_size > bias.size()[0]:
                bias = bias.repeat(res_size)
            final_weights.append(weight)
            final_biases.append(bias)

        weight = torch.cat(final_weights, 0)
        bias = torch.cat(final_biases, 0)
        return weight, bias


class TrainablePolyNetInstance(FunctionalModule):
    """
    Instance of polymorphic network
    """

    def __init__(self, poly_net, instance):
        super().__init__()
        self.instance = instance
        self.poly_net = poly_net
        self.pointer = DataPointer.start    # Trainable nets use only function arguments
        self.add_module('poly', poly_net)

    def forward(self, data_bag):
        res = self.poly_net(self.instance, data_bag)
        return res
