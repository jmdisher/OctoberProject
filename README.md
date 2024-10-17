# OctoberProject

A collection of experiments related to sparse data voxel video games slowly being developed into a framework for developing such games.

[See more details on the project wiki](https://github.com/jmdisher/OctoberProject/wiki)


## How to build

Clone the repository and run `mvn clean install` in the root directory.  This will build all components and run all tests.


## Remote Maven repo

In order to build against OctoberProject, include these lines to your pom:

```
	<repositories>
		<repository>
			<id>october-repo</id>
			<url>https://github.com/jmdisher/OctoberProject/raw/maven/repo</url>
		</repository>
	</repositories>
```

Valid versions:

* 1.0
