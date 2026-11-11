(ns application.markup
  "The markup Replicant renders. A view writes text as `[:i18n …]` and a block as an alias; one walk puts the text
   in and expands the aliases before the render (ADR-0028)."
  (:require
   [i18n :as i18n]
   [markup :as markup]
   [replicant.alias :as alias]))


(defn- expansion
  "The markup the alias `node` stands for, with the alias's `:replicant/key` on its root, as Replicant's own
   expansion puts it; nil for a node that is no registered alias."
  [node]
  (when-let [alias-function (and (vector? node)
                                 (qualified-keyword? (first node))
                                 (get (alias/get-registered-aliases) (first node)))]
    (let [[_ & content] node
          [attrs children] (if (map? (first content))
                             [(first content) (rest content)]
                             [{} content])
          expanded (alias-function attrs children)
          key (:replicant/key attrs)]
      (cond
        (not (and key (vector? expanded))) expanded
        (map? (second expanded)) (assoc-in expanded [1 :replicant/key] key)
        :else (into [(first expanded) {:replicant/key key}] (rest expanded))))))


(defn renderable
  "`markup` as Replicant renders it: the text of `language` in, every alias expanded. The aliases are expanded
   here, not in the render: Replicant skips an alias whose attributes did not change, and the text it writes would
   stay in the last language."
  [language markup]
  (markup/walk (fn [node]
                 (or (i18n/text language node)
                     (expansion node)))
               markup))
