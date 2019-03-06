# R(andom)A(ccess)P(arallel)IO


## What
A tiny library for doing parallel IO against local files in Clojure. It's all based on `java.io.RandomAccessFile`, and predicated on the fact that pretty much nobody uses mechanical hard-drives (HDD) anymore.

## Where

[![Clojars Project](https://img.shields.io/clojars/v/rapio.svg)](https://clojars.org/rapio)

## Usage
Only one namespace is required for typical usage:

### rapio.core

#### pslurp
A parallel version of `clojure.core/slurp`, which will work against a String, File \& URI/URL (referring to a local file), as long as it's smaller than 2GB. 
It can (optionally) return the raw bytes.
The level of parallelism is controlled via `:threads` and defaults to `(.availableProcessors (Runtime/getRuntime))`.


#### pslurp-big
Similar to `pslurp`, but intended to be used with files larger than 2GB. Always returns a sequence of byte-arrays.

##### Usage
```clj
(let [big-file  "/home/user/up-to-2GB.dat"
      huge-file "/home/user/up-to-8TB.dat"]
  
  ;; pslurp with 4 threads returning byte-array
  (pslurp big-file 
          :raw-bytes? true 
          :threads 4)  ;; => a byte-array
          
  ;; pslurp with all available cores returning (UTF-8) String
  (pslurp big-file)  ;; => a String
  
  (pslurp-big huge-file)  ;; => a seq of byte-arrays        
  )

```

#### pspit
A parallel version of `clojure.core/spit`, which will work against a String or byte-array content. Destination can be a String, File \& URI/URL (referring to a local file).

#### pspit-big
Similar to `pspit`, but intended to be used against multiple contents, whose concatenation wouldn't fit in a single String or byte-array (i.e. larger than 2GB).

##### Usage

```clj
(let [large-file "/home/.../.../.../update.zip" ;; 2.2GB
      arrays (pslurp-big large-file)
      lengths (map alength arrays)
      lengths-sum (apply + lengths)
      large-file-copy (str large-file "-DELETEME")]
    (try
      (pspit-big large-file-copy arrays)

      (and
        (= lengths-sum ;; didn't miss any bytes when pslurping-big
           (internal/local-file-size large-file))

        (= lengths-sum ;; didn't miss any bytes when pspiting-big
           (internal/local-file-size large-file-copy)))

      (finally
        (jio/delete-file large-file-copy)))
    ) 
 => true
```


## Benchmarks

### System

* **Intel Core i5-4200M** (2C/4T)
* **Samsung SSD Evo 840**

### Reading (based on `criterium`)

```clj
;; we'll be reading this 2.5MB file
(-> "/home/dimitris/Desktop/words.txt" io/file .length) => 2493110
```

#### clojure.core/slurp (baseline) \[5.74ms\]

```clj
;; establish a baseline using `clojure.core/slurp`
(bench 
  (slurp "/home/dimitris/Desktop/words.txt" :buffer-size 2493110))
```

```
Evaluation count : 10440 in 60 samples of 174 calls.
             Execution time mean : 5.744526 ms
    Execution time std-deviation : 27.369778 µs
   Execution time lower quantile : 5.691546 ms ( 2.5%)
   Execution time upper quantile : 5.790138 ms (97.5%)
                   Overhead used : 1.676784 ns

Found 1 outliers in 60 samples (1.6667 %)
	low-severe	 1 (1.6667 %)
 Variance from outliers : 1.6389 % Variance is slightly inflated by outliers
 ```

#### rapio.core/pslurp (2 threads) \[2.94ms\]

```clj
(bench 
  (pslurp "/home/dimitris/Desktop/words.txt" :threads 2))
```

```
Evaluation count : 20160 in 60 samples of 336 calls.
             Execution time mean : 2.942784 ms
    Execution time std-deviation : 43.569728 µs
   Execution time lower quantile : 2.865612 ms ( 2.5%)
   Execution time upper quantile : 3.017564 ms (97.5%)
                   Overhead used : 1.676784 ns
```

On this particular system (two real cores) and somewhat small file, using more threads won't provide much benefit. 

### Writing (based on `clojure.core/time` for obvious reasons)

We'll be writing the following 2.5MB String

```clj
(def content
  (->> \a               ;; the actual content doesn't matter       
       (repeat 2493110) ;; same number of bytes as for reading
       (apply str)))
```

### clojure.core/spit (baseline) \[36.58ms\]

```clj
;; establish a baseline using `clojure.core/pspit`
(time 
  (spit "/home/dimitris/Desktop/a_lot_of_as.txt" content))
```

```
Elapsed time: 36.58133 msecs
```

### rapio.core/pspit \[18.02ms\]

```clj
(time 
  (pspit "/home/dimitris/Desktop/a_lot_of_as.txt" content :threads 2)))
```

```
Elapsed time: 18.027583 msecs
```

## Hyper-threading
If your CPU supports hyper-threading, my advice would be to override the default `:threads` parameter with the number of your true cores, or less. In my personal testing (on two different CPUs), I didn't find any evidence of hyper-threading being helpful here. 

## TL;DR
Solid State Drives are truly random-access, and therefore, one can benefit significantly from doing parallel (up to a certain extent) IO on them. If you are looking for one of the following, this library may be of help to you ;)

* Faster (by means of parallelism) alternatives to the built-in `slurp/spit`, that will only work on local files.
* A way to read/write something that fits in your available RAM, but doesn't fit in a single String or byte-array. 

The level of parallelism can be controlled in the former case, but not in the latter. As always, you should perform your own benchmarks depending on the task at hand. 

## License

Copyright © 2019 Dimitrios Piliouras

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
