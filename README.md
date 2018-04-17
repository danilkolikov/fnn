# FNN
![apm](https://img.shields.io/apm/l/vim-mode.svg)

**F**unctional **N**eural **N**etworks - experimental language for designing of neural networks, capable of solving 
algorithmic problems. 

The project is in the early pre-alpha stage, you can support development by testing and bug-reporting.

### Motivation

Existing architectures of neural networks can solve specific problems such as classification and regression. 
There are few possible approaches to the network design which allow networks to do more complex tasks such as operations on data. 
For example, Differential Neural Computer and Neural Turing Machine have shown that they can learn to solve such 
problems. Also it was experimentally shown that the choice of architecture of the network can have a big impact on it's 
performance. 

My approach is based on assumption that a finely-tuned architecture of the network can allow it to learn how to 
infer algorithm. The goal of this project is to simplify development of such architectures


### Approach

There is a deep similarity between neural networks and functional programming. Specifically, in the article 
["Neural Networks, Types, and Functional Programming"](http://colah.github.io/posts/2015-09-NN-Types-FP/) 
it was shown that deep RNNs are similar to second-order functions. This project was inspired by this analogy. 

Connections between worlds of functional languages and neural networks seem to be quite interesting. For example, 
lambda-expressions correspond to overall structure of neural networks, and algebraic data types correspond to structures of 
network layers and activation functions. All connections will be (eventually) explained in Medium articles.

**FNN** language is based on this correspondence between lambda-expressions and neural networks. FNN-programs 
can be compiled to neural networks, and the result of the evaluation of the network will be equal to a result of
reduction of the program. What's more, FNN-programs can contain "holes" - parts with unknown logic. The exact logic 
behind "hole" can be found by back-propagation and learning by examples

To infer the algorithm using FNN one can do the following:

1. Write a sketch of algorithm in FNN language. Sketch may have "holes" and should only express
   the basic structure of the algorithm. E.G., it should tell will algorithm use recursion, pattern-matching, etc
2. Compile algorithm to a neural network. FNN-compiler creates PyTorch specifications of networks
3. Train created network using dataset and result values, evaluate performance, etc

So inference of algorithms will look the same as training of usual neural network, but with extra step - sketching the 
algorithm in FNN language and compilation to Python code

### Language

FNN is a functional language with Hindley-Milner type system, algebraic data types, pattern matching and fixed point combinator.
Language supports few constructions common for the most of functional languages, they will be explained by examples:

#### Type Definitions
```
-- To define type, use keyword @type 
-- Name of type and type constructor should start with a capital letter
-- Note that every line should end with a semicolon
@type Bool = False | True; 

-- Types may have parameters
@type Pair a b = MkPair a b;

-- And can be quite complex
@type StrangeType a b c = MkFirst (Pair a b) | MkSecond (Pair b c) | MkThird Bool;
```

#### Lambda expressions
```
-- Definition of expressions is similar to a ones in haskell
-- Main difference is that every keyword should start with @
id x = x;

-- Pattern matching is supported
if True x y = x;
if False x y = y;

-- Even a complex one
fst (MkPair x y) = x;

-- Expressions may contain anonymous functions
const = \x y. x;

-- Expressions may be applied to each other
-- Note that evaluation of expressions is EAGER, so partial application is not supported
test = const (fst (MkPair True False)) True;

-- But you can overcome this difficulty with eta-abstraction
test1 x = const True x;

-- Let-expressions are supported, bindings should be separated by commas
-- Every bound expression gets the most abstract type, so it can be reused 
-- in different places of resulting expression
test_let = @let id = \x. x, id1 = id id @in id1 True;

-- Recursion is supported too. @rec represents a Y-combinator
-- it bounds a name which can be used in expression, and replaced with expression during evaluation
test_rec = @rec f @in \x. f x;

-- Also if the name of function is used in it's body, Y-combinator is added implicitly
impl_rec True = False;
impl_rec False = impl_rec True;

-- Type of expression may be specified manually in different ways
-- To specify type of the whole expression, use @type keyword
@type myId = Bool -> Bool;
myId x = x;

-- You may also specify type of sub-expression in commas after colon
anotherMyId = \x. id (x: Bool);
```

#### Holes
```
-- To add a hole to expression, use @learn keyword
fancyId = @learn;

-- Hole is an expression with unknkown type and unknown body. It will be found during back-propagation
-- Expression may contain many holes, every hole is learned separately from others
@type and = Bool -> Bool -> Bool;
and x y = if x (@learn y) (@learn y)
```

### Compilation

During compilation an FNN-program is type-checked and structure of the network is computed. The result of compilation
is a python-file with the code representing networks. As the project is not finished, there are some limitations on 
the correct FNN-program:

- Every lambda-expressions should be fully applied, as partial application is not supported. 
  It means that if we have `(f x y z)` then `f` should accept exactly 3 arguments
- Python code is generated only for fully instantiated expressions. That means that network `id x = x` will not be
  generated as we don't know the exact type of `x`. But the network for `id x = (x: Bool)`  will be created
- Algebraic Data Types can be instantiated only with ADTs. That means that we can't have a pair of functions
- Recursive ADTs are not supported
- Compiled files use `runtime` library. You should copy the folder `src/main/python/runtime` to a directory with 
  generated files
  
Now error messages are quite unreadable, but if you know about these limitations, you will not encounter strange errors
(hopefully)

### Why do we need it?

You may ask, why do we need FNN if PyTorch already supports custom evaluation graphs? That's true that the similar nets for 
algorithm inference can be written in a pure python. FNN has some good features which will simplify the development:

- Type inference checks that every connected layers will have appropriate sizes
- Exact size of every layer can be found from type information, so there is no need to specify it manually
- Polymorphic types helps us to use the same logic for different sizes of networks
- Using polymorphic @learn we can learn behaviour that doesnt depend on current type
- Language helps to think in a more abstract way than implementing the network in a pure python

### How to use it?

The project now is not yet finished, so you need an IDE to run the compiler. The preferred choice is IntelliJ IDEA.

Command-line options for compiler are following:

```bash
"file1.fnn" "file2.fnn" -d "output/directory" -v
```

    Specify the list of files to compile before other commands
    -d stands for output directory
    -v enables verbose output with type information and the list of generated instances
    
Compiler checks types of expressions, their correctness and generates Python files with the structure of the network in 
the output directory. Every file has similar structure:

```python
# Imports
from .runtime import ...

# Type Specifications
Bool = TypeSpec(operands=[
    LitSpec(0),
    LitSpec(1),
])

# Poly-net specifications
swap_0_polynet = TrainablePolyNet([2, 'a', 'b'], ['b', 2, 'a'])

# Update instances - this method should be called after each backprop on net with polymorphic @learn
def update_instances():
    swap_0_polynet.update_instances()
    
# Net Specifications
Just_Bool_net = ConstructorLayer(2, 3, 0)

if_Bool_net = GuardedLayer(cases=[
    GuardedLayer.Case(
        [
            ObjectPattern("True", 1),
            VariablePattern("x", Bool, 2, 4),
            VariablePattern("y", Bool, 4, 6),
        ],
        VariableLayer.Data(2, 4)        
    ),
    GuardedLayer.Case(
        [
            ObjectPattern("False", 0),
            VariablePattern("x", Bool, 2, 4),
            VariablePattern("y", Bool, 4, 6),
        ],
        VariableLayer.Data(4, 6)        
    ),
])
```

As you can see, file uses runtime library. As the project is not finished yet, you should manually copy the library from
the repository to the directory with output files. Runtime library is located in `src/main/python/runtime`

After compilation, you can import definitions of networks from generated files, train them and use. There are few notes
which can be useful:

- After each backpropagation on the network with polymorphic `@learn`-expression you should call method `update_instances()`.
  It updates instances of this expression and is required because of the way PyTorch computes gradients
- To generate train data, you may iterate over types (iteration will yield vectors representing objects) or convert
  type to tensor using method `to_tensor()`. It will create a matrix that contains all possible objects of this type
- To feed the network with data, use method `my_net.call(my_data)`. Usual way of doing it - `my_net(my_data)` no longer 
  works, because functional networks can be called not only with vectors, but with other networks as well. To finely
  tune import data of the network, you can use `DataBag`-s: `my_net(DataBag(my_data, my_nets))` where `my_nets` is an
  array of networks
- Result of the network is a tensor, so usual loss functions can be used with it

Here is an example of the train method:

```python
import torch
from torch.autograd import Variable
import test   # test is a compiled module

def train(net, data, res, iterations=10):
    data = Variable(data, requires_grad=False)
    res = Variable(res, requires_grad=False)
    loss_fn = torch.nn.MSELoss(size_average=False)
    learning_rate = 1
    
    optimizer = torch.optim.Adam(net.parameters(), lr=learning_rate)
    for i in range(iterations):
        pred = net(DataBag(data, []))
        loss = loss_fn(pred, res)
        print (i, loss.data[0])
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
        test.update_instances() # Update instances after each backpropagation
```