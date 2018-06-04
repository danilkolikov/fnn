import torch

from .base import FunctionalModule
from ..data import DataPointer, DataBag
from ..trees import make_tuple


class GuardedLayer(FunctionalModule):
    """
    Net with many possible ways of execution, every possibility is chosen according on
    similarity of data and pattern
    """

    def __init__(self, cases, mismatch_handler):
        super().__init__()
        self.pointer = DataPointer.start
        self.cases = cases
        self.mismatch_handler = mismatch_handler
        for idx, case in enumerate(cases):
            self.add_module(str(idx), case)

    def forward(self, data_bag):
        results = list(filter(
            lambda res: res is not None,
            map(lambda case: case.forward(data_bag), self.cases)
        ))
        if len(results) == 0:
            # Input doesn't match any of alternatives, call handler
            return self.mismatch_handler.forward(data_bag)
        result = results[0]
        for case_result in results[1:]:
            result = result + case_result
        return result

    class Case(FunctionalModule):

        EPS = 1e-3

        def __init__(self, pattern, net):
            super().__init__()
            self.pattern = pattern
            self.net = net
            self.add_module('net', net)

        def forward(self, data_bag):
            (presence, trees) = self.pattern.get_trees(data_bag.data)
            execute_rows = presence.data > self.EPS
            size = data_bag.size

            if execute_rows.any():
                # Case can be executed
                selected_size = execute_rows.sum().item()
                new_tensor = make_tuple(trees)
                # Select rows
                if len(trees) == 0:
                    selected = new_tensor
                else:
                    selected = new_tensor.select_rows(execute_rows)
                new_data = DataBag(selected, data_bag.nets, selected_size)
                result = self.net.forward(new_data)
                # Add rows which were dropped
                rows, columns = result.tensor.size()
                matrix = torch.zeros(size, rows)
                cur = 0
                for i in range(size):
                    if execute_rows[i].item():
                        matrix[i, cur] = 1
                        cur += 1
                added = result.apply(lambda t: matrix.mm(t))
                multiplied = added.cmul(presence.view(size, 1))
                return multiplied
            else:
                # Pattern-matching didn't succeed - return None
                return None
