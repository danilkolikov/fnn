import torch
from torch.nn import Module, MSELoss


class StructuredLoss(Module):
    """
    Applies MSE loss to layers of TensorTree and sums results
    """

    def __init__(self):
        super().__init__()
        self.loss = MSELoss(reduce=False)

    def forward(self, a, b):
        loss = self._apply_loss(a, b)
        # print(a, b, loss)
        return loss.sum()

    def _apply_loss(self, a, b):
        loss = self.loss(a.tensor, b.tensor).sum(1)
        # Compute loss for children
        if len(a.children) != len(b.children):
            raise ValueError('Mismatching sizes of children: ' + str(len(a.children)) + ' and ' + str(len(b.children)))
        for (cur, (a_child, b_child)) in enumerate(zip(a.children, b.children)):
            if a_child is None:
                continue
            if b_child is None:
                # Second children is missing - create fake one
                rows, columns = a_child.tensor.size()
                b_child = a_child.__class__(
                    torch.zeros(rows, columns),
                    [None] * columns
                )
            # child_loss = self._apply_loss(a_child.cmul(a.tensor[:, cur].view(a.rows(), 1)), b_child)
            child_loss = self._apply_loss(a_child, b_child)

            # Add to resulting loss
            loss = loss + child_loss
        return loss
