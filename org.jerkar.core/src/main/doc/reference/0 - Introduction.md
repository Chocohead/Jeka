# Introduction

This document stands for reference guide and provides details about Jerkar behaviour. If you are looking for 
how exactly Jerkar behaves or you want to get a pretty exhaustive list of Jerkar features, you are in the right place.

However, a document can not replace a source code or API for exhaustion. Jerkar philosophy is to be **as transparent and 
easy to master as possible**. We hope that no user will ever feel the need to buy some trainings or books to master it.

The source code has been written with intelligibility in mind in order to navigate easily from the user build code 
to the Jerkar engine room. For Java developers, reading source code and placing break points troubleshoots faster 
than documentation/support most of the time.  

**What is Jerkar ?**

Jerkar contains both a library and a tool. 

Library is for dealing with file sets, compilations, dependency management, testing, 
external processes, crypto signatures, ... in a word, all regular things you need to build/publish projects and especially Java projects.

Tool is intended to execute Java source code from the console in a parameterizable way. Its architecture eases the 
reuse of build elements (logic, settings, doc, ...) across projects. 

Although library and tool are bundled in the same jar, the library does not depend on the tool at all. It can be understood 
on its own without any knowledge of the tool part. If you are only interested in the library part you can [jump to this section](#LibraryPart).

You may ask why these two parts are not bundled in distinct jar...well, the tool is very lightweight compared 
to the lib part, so splitting drawbacks would outweigh its advantages. This decision may be reconsidered later when Jerkar will 
embrace JDK9+.

