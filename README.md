# R(andom)A(ccess)P(arallel)IO


## What
A tiny library for doing parallel IO against local files in Clojure. It's all based on `java.io.RandomAccessFile`.

## Where
FIXME

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


## Benchmarks (based on `criterium`)
### Reading

```clj
;; we'll be reading this 2.5MB file
(-> "/home/dimitris/Desktop/words.txt" io/file .length) => 2493110
```

#### clojure.core/slurp (baseline) \[5.74ms\]

```clj
;; establish a baseline using `clojure.core/slurp`
(bench (slurp "/home/dimitris/Desktop/words.txt" :buffer-size 2493110))

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
(bench (pslurp "/home/dimitris/Desktop/words.txt" :threads 2))

Evaluation count : 20160 in 60 samples of 336 calls.
             Execution time mean : 2.942784 ms
    Execution time std-deviation : 43.569728 µs
   Execution time lower quantile : 2.865612 ms ( 2.5%)
   Execution time upper quantile : 3.017564 ms (97.5%)
                   Overhead used : 1.676784 ns

```

On such a small file, using more threads won't give any benefit (I get the same mean with 2 and 3 threads).
In short, the bigger the file - the greater the benefit from using more threads. For a 2.5MB file 2 threads seems to be the sweet-spot.


### Writing

## License

Copyright © 2019 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
