# AI Pair Programming

I built this project with the help of [Claude Code](https://claude.ai/code) (Anthropic).  
It is done in a kind of "AI pair-programming".

I define the project goals, structure, languages and architecture.  
I use AI as a (very) experienced colleague for pair programming.

For each feature, I usually define the requirements and refine them with AI.  
The process is mostly :

1. **Plan** — With the help of Claude, we explore the codebase and chose the best approach;
2. **Implement** — Usually Claude writes the code following the project's conventions and architecture, but sometimes I do it;
3. **Review** — If Claude writes the code, I review it and we iterate until I'm happy;
4. **Commit** — Changes are committed;

Planning is one of the most important part.  
It's a real collaboration, where I usually give my idea, what I want to do, and oftentimes how I want to do it.  
I also ask if there are any alternatives or things that could be improved (trying to avoid the bias AI can sometimes have where you're always right).
I try to work on small features, with a clearly defined scope.
I try to be mindful of libraries that are used.

Testing is of course important, even more so when using AI.  
I try to have good tests, at the correct level (in the test pyramid).  
I try to avoid mocking, and instead have good "edge-to-edge" tests 
(this comes from the "Architecture patterns with Python book, where they define it as "essentially an integration-style 
test that drives the system from one architectural edge to another while substituting some infrastructure with fakes").

## Why be transparent about this

AI-assisted development is becoming common but not always disclosed.  
I think it's worth being upfront: the architecture decisions, code style, and product direction are human-driven; while the implementation can be largely AI-generated and human-reviewed.
