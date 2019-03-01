(defproject rapio "0.1.0-SNAPSHOT"
  :description "Random-Access Parallel IO"
  :url "https://github.com/jimpil/rapio"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :profiles {:dev {:repl-options {:init-ns rapio.core}
                   :dependencies [[org.clojure/clojure "1.10.0"]
                                  [criterium "0.4.4"]]}}
  :jar-exclusions [#"\.+exclude"]
  )
