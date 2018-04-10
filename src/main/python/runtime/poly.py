import math

import torch
from torch.autograd import Variable
from torch.nn import Module, Parameter
from torch.nn.functional import sigmoid, linear

from runtime.data import DataPointer


class TrainablePolyNetInstance(Module):
    """
    Instance of polymorphic network
    """

    def __init__(self, poly_net, instance, data_pointer):
        super().__init__()
        self.instance = instance
        self.poly_net = poly_net
        self.pointer = data_pointer
        self.add_module('poly', poly_net)

    def forward(self, data_bag):
        return self.poly_net(self.instance, data_bag)


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

        self.weights = []
        result_size = result.size if result is not None else 1
        length = 0

        # Register parameters
        for (idx, arg_type) in enumerate(arguments):
            if arg_type is None:
                # Polymorphic argument, create a 1-line array
                weight = Parameter(torch.Tensor(result_size, 1))
            else:
                weight = Parameter(torch.Tensor(result_size, arg_type.size))
            length += weight.size(1)

            self.weights.append(weight)
            self.register_parameter(str(idx) + '_w', weight)
        self.bias = Parameter(torch.Tensor(result_size))

        # Init parameters
        stdv = 1. / math.sqrt(length)
        for weight in self.weights:
            weight.data.uniform_(-stdv, stdv)
        self.instances = []
        self.bias.data.uniform_(-stdv, stdv)

    def instantiate(self, arguments_types=None, result_type=None, data_pointer=None):
        if arguments_types is None:
            arguments_types = []
        if result_type is None:
            result_type = self.result
        if data_pointer is None:
            data_pointer = DataPointer.start
        if result_type is None:
            raise ValueError('Result type is not instantiated')

        current_type = 0
        types = []
        for arg_type in self.arguments:
            if arg_type is None:
                if current_type == len(arguments_types):
                    raise ValueError("Not enough types for instantiation")
                arg_type = arguments_types[current_type]
                current_type += 1
            types.append(arg_type)

        weight, bias = self._compute_parameters(types, result_type)
        self.instances.append({
            'args': types,
            'res': result_type,
            'weight': weight,
            'bias': bias
        })

        return TrainablePolyNetInstance(self, len(self.instances) - 1, data_pointer)

    def update_instances(self):
        for instance in self.instances:
            weight, bias = self._compute_parameters(instance['args'], instance['res'])
            instance['weight'] = weight
            instance['bias'] = bias

    def forward(self, instance, data_bag, *rest):
        params = self.instances[instance]
        linear_output = linear(data_bag.data, params['weight'], params['bias'])
        # ToDo: Implement fancy type-based activation function
        return sigmoid(linear_output)

    def _compute_parameters(self, args, res):
        instance_weight = []
        for (arg_type, instance_type, weight) in zip(self.arguments, args, self.weights):
            if arg_type is None:
                if self.result is None:
                    # Both argument and result are polymorphic
                    # Weight matrix is an "identity" multiplied by weight tensor
                    this_weight = weight * Variable(torch.eye(res.size, instance_type.size))
                else:
                    # Argument is polymorphic, result is algebraic
                    # Repeat raw to fit size of instantiated type
                    this_weight = weight.repeat(1, instance_type.size)
            else:
                if self.result is None:
                    # Argument is algebraic, result is polymorphic
                    # Repeat column to fit size of result type
                    this_weight = weight.repeat(res.size, 1)
                else:
                    # Both argument and result are algebraic
                    this_weight = weight

            instance_weight.append(this_weight)

        weight = torch.cat(instance_weight, 1)
        if self.result is None:
            bias = self.bias.repeat(res.size)
        else:
            bias = self.bias
        return weight, bias

