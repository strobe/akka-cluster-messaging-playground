Akka messaging cluster playground
=================================

## Summary

Clustering nodes messaging simulation with some kind of benchmarking stats.

At start it launched 3 nodes + master app node.

Nodes doing logging of messages to /log/akka.log while app is running.

## Releases

See repository releases page for Jar distribution with instructions

### Known Issues

- Because it runs so many count of ActorSystems on single machine
  and single JVM this app is used huge count of threads.
  (one node == ActorSystems)

- For minimize threads usage it's used shared dispatcher between ActorSystems

- 'get_stats' command shows only approximately estimations not real values

- 'add_node', 'add_nodes', 'remove_node', 'remove_nodes' commands doesn't check
   confirmation from cluster event about nodes creations/removes

- akka-cluster is more suitable for launching nodes on separated machines,
  therefore for optimize this approach for better performance on single machine
  is more right way is utilising single actors system (and simple actors as nodes)

## Running in sbt
run following in terminal:

    'sbt run'

see log in ./log/akka.log

## Example of command line session
    [info] node Actor[akka://ClusterSystem/user/worker#-72748346] has been added
    [info] node Actor[akka://ClusterSystem/user/worker#2019401759] has been added
    [info] node Actor[akka://ClusterSystem/user/worker#-1535320854] has been added
    [info] For messages stats see: log/akka.log
    [info]
    [info] Commands:
    [info]   help                  - this help text
    [info]   quit                  - exit from app
    [info]   nodes_count           - return cont of cluster nodes
    [info]   nodes_list            - show list of cluster nodes addresses
    [info]   set_timeout [ms]      - set messages sending timeout in milliseconds
    [info]   add_node              - adds single node
    [info]   add_nodes [count]     - adds multiply nodes
    [info]   remove_node           - remove single node
    [info]   remove_nodes [count]  - remove multiply nodes
    [info]   get_stats [countOfNewNodesInStep] - returned analytics information and created specific count of nodes during it
    [info]   get_stats             - returned analytics information
    [info] >>
    [info] unknown command
    [info] >> nodes_list
    [info] Nodes:\nakka.tcp://ClusterSystem@127.0.0.1:2552
    [info] akka.tcp://ClusterSystem@127.0.0.1:56549
    [info] akka.tcp://ClusterSystem@127.0.0.1:56552
    [info]
    [info] >> nodes_count
    [info] Nodes count: 3
    [info] >> set_timeout 50
    [info] SetTimeout message sent with: 50 ms
    [info] >> add_node
    [info] node Actor[akka://ClusterSystem/user/worker#-1665027242] has been added
    [info] >>  add_node 3
    [info] unknown command
    [info] >> add_nodes 3
    [info] node Actor[akka://ClusterSystem/user/worker#1737632268] has been added
    [info] node Actor[akka://ClusterSystem/user/worker#1698102864] has been added
    [info] node Actor[akka://ClusterSystem/user/worker#949316944] has been added
    [info] >> remove_node
    [info] node removed
    [info] >> remove_nodes 2
    [info] node removed
    [info] node removed
    [info] >> add_node
    [info] node Actor[akka://ClusterSystem/user/worker#-458497476] has been added
    [info] >> nodes_count
    [info] Nodes count: 5
    [info] >> get_stats
    [info] >> please wait it will take a while ...
    [info] Roughly Tests Stats:
    [info] ---------------------------------------------------------------------------------------
    [info] |nodes count | messages in cluster/sec | messages per node/sec | sending timeout (ms) |
    [info] ---------------------------------------------------------------------------------------
    [info] | 4          | 648                     | 162                   | 1                    |
    [info] | 5          | 955                     | 191                   | 5                    |
    [info] | 6          | 1182                    | 197                   | 10                   |
    [info] | 7          | 1456                    | 208                   | 15                   |
    [info] | 8          | 1704                    | 213                   | 20                   |
    [info] | 9          | 1935                    | 215                   | 25                   |
    [info] | 10         | 1630                    | 163                   | 30                   |
    [info] | 11         | 803                     | 73                    | 35                   |
    [info] | 12         | 588                     | 49                    | 40                   |
    [info] | 13         | 468                     | 36                    | 45                   |
    [info] | 14         | 420                     | 30                    | 50                   |
    [info] | 15         | 405                     | 27                    | 55                   |
    [info] | 18         | 378                     | 21                    | 70                   |
    [info] | 19         | 361                     | 19                    | 75                   |
    [info] | 21         | 357                     | 17                    | 85                   |
    [info] | 22         | 352                     | 16                    | 90                   |
    [info] | 23         | 322                     | 14                    | 95                   |


### Build
run following in terminal:

    'sbt assembly'

### Running jar
run following in terminal at root dir:

    'java -Djava.library.path="./sigar" -jar target/scala-2.11/akka-messaging.jar'

