package models.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Node
import org.overviewproject.tree.orm.stores.{ BaseStore, NoInsertOrUpdate }
import models.orm.Schema

object NodeStore extends BaseStore(models.orm.Schema.nodes) with NoInsertOrUpdate[Node]
