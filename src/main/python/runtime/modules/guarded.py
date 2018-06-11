import torch

from .base import FunctionalModule
from ..data import DataBag
from ..trees import make_tuple


class GuardedLayer(FunctionalModule):
    """
    Net with many possible ways of execution, every possibility is chosen according on
    similarity of data and pattern
    """

    def __init__(self, cases, mismatch_handler, pointer):
        super().__init__()
        self.pointer = pointer
        self.cases = cases
        self.mismatch_handler = mismatch_handler
        for idx, case in enumerate(cases):
            self.add_module(str(idx), case)
            case.pointer = pointer

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

        SELECT_ROWS = False

        def __init__(self, pattern, net):
            super().__init__()
            self.pattern = pattern
            self.net = net
            self.add_module('net', net)
            self.pointer = None

        def forward(self, data_bag):
            # Split data_bag.data into 2 trees - one before patterns, another after them
            before, after = data_bag.split(self.pointer)
            (presence, trees) = self.pattern.get_trees(after.data)
            size = data_bag.size
            if presence is not None:
                # Case can be executed
                new_tensor = make_tuple(trees)
                if self.SELECT_ROWS:
                    execute_rows = presence.data > self.EPS
                    selected_size = execute_rows.sum().item()
                    # Select rows
                    if len(trees) == 0:
                        selected = new_tensor
                    else:
                        selected = new_tensor.select_rows(execute_rows)
                    if len(before.data.children) == 0:
                        selected_before = before.data
                    else:
                        selected_before = before.data.select_rows(execute_rows)
                    before_part = DataBag(selected_before, before.nets, selected_size)
                    new_data = before_part.append(DataBag(selected, after.nets, selected_size))
                    result = self.net.forward(new_data)
                    # Add rows which were dropped
                    rows, columns = result.tensor.size()
                    matrix = torch.zeros(size, rows)
                    cur = 0
                    for i in range(size):
                        if execute_rows[i].item():
                            matrix[i, cur] = 1
                            cur += 1
                    net_result = result.apply(lambda t: matrix.mm(t))
                else:
                    new_data = before.append(DataBag(new_tensor, after.nets, size))
                    net_result = self.net.forward(new_data)
                multiplied = net_result.cmul(presence.view(size, 1))
                return multiplied
            else:
                # Pattern-matching didn't succeed - return None
                return None
