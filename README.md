# platform-rocket
A StreamBlocks platform for Rocket-Chip

How to compile :

First clone StreamBlocks:

```
git clone https://github.com/streamblocks/streamblocks-tycho streamblocks-tycho
cd streamblocks-tycho
git checkout typesystem
mvn -DskipTests install
```

Then clone this repository:

```
git clone https://github.com/streamblocks/rocket-chip rocket-chip
cd rocket-chip
mvn install
```