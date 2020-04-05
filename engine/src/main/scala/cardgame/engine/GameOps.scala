package cardgame.engine

import cats.effect.IO
import cardgame.model._

object GameOps {
  implicit final class _GameOps(val game: Game) extends AnyVal {

    def join(player: JoiningPlayer): (Game, Event) = {
      game match {
        case StartingGame(playersJoined) =>
          if (playersJoined.contains(player))
            game -> PlayerJoined(player.id)
          else
            StartingGame(playersJoined :+ player) -> PlayerJoined(player.id)
        case _ =>
          game -> InvalidAction(player.id)
      }
    }

    def start(deck: Deck, randomizer: IO[Int]): (Game, Event) = {
      game match {
        case StartingGame(players) =>
          val random = randomizer.unsafeRunSync() % players.size
          val gamePlayers = players.map(j => PlayingPlayer(j.id, List.empty))
          val startingPlayer = random
          val startedGame =
            StartedGame(
              gamePlayers,
              deck,
              startingPlayer,
              Clockwise,
              List.empty,
              DiscardPile.empty
            )
          startedGame -> GameStarted(gamePlayers(startingPlayer).id)
        case _ =>
          game -> InvalidAction()
      }

    }

    def end: (Game, Event) =
      game match {
        case StartingGame(players) =>
          ForcedGameEnd(players) -> GameStopped()
        case sg: StartedGame if sg.players.size == 1 =>
          val winner = sg.players(0)
          EndedGame(winner) ->(GameFinished(winner.id))
        case sg: StartedGame =>
          ForcedGameEnd(sg.players) -> GameStopped()
      }

    def draw(playerId: PlayerId): (Game, Event) = game match {
      case sg @ StartedGame(players, deck, nextPlayer, _, _, _) =>
        ifHasTurn(players, nextPlayer, playerId, {
            val player = players.find(_.id == playerId).get
            val draw = deck.cards.take(1)
            val newDeck = Deck(deck.cards.drop(1), List.empty)
            val newHand = player.copy(hand = player.hand ++ draw)
            sg.copy(players = players.updated(nextPlayer, newHand), deck = newDeck) ->
              draw.headOption.map(c => GotCard(playerId, c.id))
                .getOrElse(InvalidAction(playerId))
          },
          game
        )
      case _ =>
        game -> InvalidAction(playerId)
    }

    def bottomDraw(playerId: PlayerId): (Game, Event) = game match {
      case sg @ StartedGame(players, deck, nextPlayer, _, _, _) =>
        ifHasTurn(players, nextPlayer, playerId,
          {
            val player = players.find(_.id == playerId).get
            val draw = deck.cards.takeRight(1)
            val newDeck = Deck(deck.cards.dropRight(1), List.empty)
            val newHand = player.copy(hand = player.hand ++ draw)
            sg.copy(players = players.updated(nextPlayer, newHand), deck = newDeck) ->
              draw.headOption.map(c => GotCard(playerId, c.id)).getOrElse(InvalidAction(playerId))
          },
          game
        )
      case _ =>
        game -> InvalidAction(playerId)
    }

    def endTurn(playerId: PlayerId): (Game, Event) = game match {
      case sg @ StartedGame(players, _, currentPlayer, direction, _, _) =>
        ifHasTurn(
          players, currentPlayer, playerId,
          {
            val nextPlayer = shift(players.size, currentPlayer, direction)
            sg.copy(nextPlayer = nextPlayer) -> NextPlayer(players(nextPlayer).id)
          },
          game
        )
      case _ =>
        game -> InvalidAction(playerId)
    }

    private def shift(numberOfPlayers: Int, currentPlayerIndex: Int, direction: Direction): Int =
      direction match {
        case Clockwise => shiftRight(numberOfPlayers, currentPlayerIndex)
        case AntiClockwise => shiftLeft(numberOfPlayers, currentPlayerIndex)
      }

    private def shiftRight(size: Int, current: Int): Int =
      if (current == size - 1)
        0
      else
        current + 1

    private def shiftLeft(size: Int, current: Int): Int =
      if (current == 0)
        size - 1
      else
        current - 1

    private def hasTurn(players: List[PlayingPlayer], nextPlayer: Int, playerTryingToPlay: PlayerId): Boolean =
      players.zipWithIndex.find(_._1.id == playerTryingToPlay).exists(_._2 == nextPlayer)


    def leave(playerId: PlayerId): (Game, Event) = game match {
      case sg @ StartedGame(players, _, currentPlayer, _, _, _) =>
          ifHasTurn(
            players, currentPlayer, playerId,
            sg.copy(players.filterNot(_.id == playerId)) -> PlayerLeft(playerId),
            sg
          )
      case _ =>
        game -> InvalidAction(playerId)
    }

    def play(playerId: PlayerId, cardId: CardId): (Game, Event) = game match {
      case sg @ StartedGame(players, _, currentPlayer, _, _, _) =>
        ifHasTurn(
          players, currentPlayer, playerId,
          {
            val player = players.find(_.id == playerId).get
            val event = player.hand.find(_.id == cardId).map {
              card => PlayedCard(VisibleCard(cardId, card.image), playerId)
            }.getOrElse(InvalidAction(playerId))
            sg.copy(
              players.updated(
                currentPlayer,
                player.copy(hand = player.hand.filterNot(_.id == cardId))
              )
            ) -> event
          },
          sg
        )
      case _ =>
        game -> InvalidAction(playerId)
    }

    def reverse(playerId: PlayerId): (Game, Event) = game match {
      case sg @ StartedGame(players, _, currentPlayer, direction,_,_) =>
        ifHasTurn(players, currentPlayer, playerId,
          sg.copy(direction = direction.reverse) -> NewDirection(direction.reverse),
          sg
        )
      case other =>
        other -> InvalidAction(playerId)
    }

    def borrow(player: PlayerId): (Game, Event) = game match {
      case sg @ StartedGame(players, deck, currentPlayer, _, _, _) =>
        ifHasTurn(players, currentPlayer, player, {
            val borrowDeck = deck.borrow
            borrowDeck.borrowed.lastOption.map {
              c => sg.copy(deck = borrowDeck) -> BorrowedCard(c.id, player)
            }.getOrElse(sg -> InvalidAction(player))
          },
          sg
        )
      case other =>
        other -> InvalidAction(player)
    }

    def returnCard(player: PlayerId, card: CardId): (Game, Event) =  game match {
      case sg @ StartedGame(players, deck, currentPlayer, _, _, _) =>
        ifHasTurn(players, currentPlayer, player,
          {
            val borrowedDeck = deck.returnCard(card)
            borrowedDeck.map {
              d => sg.copy(deck = d) -> ReturnedCard(card)
            }.getOrElse(sg -> InvalidAction(player))
          },
          sg
        )
      case _ =>
        game -> InvalidAction(player)
    }

    def steal(player: PlayerId, from: PlayerId, cardIndex: Int): (Game, Event) = game match {
      case sg @ StartedGame(players, _, currentPlayer, _, _, _) =>
        ifHasTurn(players, currentPlayer, player,
          {
            val playerTo = players.find(_.id == player).get
            val position = players.indexOf(playerTo)
            val playerFrom = players.find(_.id == from)
            val cardToSteal = playerFrom.flatMap {
              p =>
                p.hand.lift(cardIndex)
            }
            val event = cardToSteal.map(MoveCard(_, from, player)).getOrElse(InvalidAction(player))
            val playerWithCard = cardToSteal.map(c => playerTo.copy(hand = playerTo.hand :+ c)).getOrElse(playerTo)
            sg.copy(players = players.updated(position, playerWithCard)) -> event
          },
          sg
        )
      case other =>
        other -> InvalidAction(player)
    }

    def action(gameAction: Action, randomizer: IO[Int]): (Game, Event) = {
      gameAction match {
        case jg: JoinGame =>
          join(jg.player)
        case EndGame =>
          end
        case StartGame(deck) =>
          start(deck, randomizer)
        case DrawCard(playerId) =>
          draw(playerId)
        case PlayCard(cardId: CardId, playerId) =>
          play( playerId, cardId)
        case EndTurn(playerId) =>
          endTurn(playerId)
        case Leave(playerId) =>
          leave(playerId)
        case SwitchDirection(playerId) =>
          reverse(playerId)
        case BottomDraw(playerId) =>
          bottomDraw(playerId)
        case BorrowCard(player) =>
          borrow(player)
        case ReturnCard(playerId, cardId) =>
          returnCard(playerId, cardId)
        case StealCard(player, from, cardIndex) =>
          steal(player, from, cardIndex)
        case p: PlayingGameAction =>
          game -> InvalidAction(p.player)
        case _ =>
          game -> InvalidAction()
      }
    }

    private def ifHasTurn(p: List[PlayingPlayer], ind: Int, pId: PlayerId, t: => (Game, Event), defaultGame: Game): (Game, Event) =
      Either.cond(
        hasTurn(p, ind, pId),
        t,
        defaultGame -> InvalidAction(pId)
      ).merge
  }
}