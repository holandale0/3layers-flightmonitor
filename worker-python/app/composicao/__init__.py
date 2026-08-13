"""Raiz de composicao: o unico lugar que conhece as duas camadas.

E aqui que se decide QUAL adaptador atende cada porta — fonte real ou
falsa, modelo de linguagem ou regras. O controle depende so das portas, e
por isso nao pode importar daqui nem dos adaptadores.

No core-java este papel e do Spring, com as classes @Configuration. Em
Python, sem container, ele precisa de um lugar proprio — senao a fabrica
seria uma excecao permanente a regra de camadas.
"""
