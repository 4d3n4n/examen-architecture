# Choix du Design Pattern

Pour le microservice **Reservation Service**, j'ai mis en place le **State Pattern** (Pattern Comportemental).

## Justification du choix

### Problématique
Le cycle de vie d'une réservation passe par différents états bien définis :
- `CONFIRMED` (au moment de la création)
- `CANCELLED` (si l'utilisateur annule la réservation)
- `COMPLETED` (si la réservation s'est déroulée et s'est terminée correctement)

Dans le futur, d'autres états pourraient s'ajouter (ex: `PENDING_PAYMENT`, `IN_PROGRESS`, etc.), ce qui pourrait rendre la classe de service complexe avec de nombreuses conditions (if/else ou switch) pour gérer les transitions d'état.

### Solution apportée par le State Pattern
Le **State Pattern** permet à un objet de modifier son comportement lorsque son état interne change. L'objet donnera l'impression de changer de classe.

Dans notre implémentation :
1. L'interface `ReservationState` définit un contrat pour la gestion d'un état.
2. Les classes concrètes `ConfirmedState`, `CancelledState`, et `CompletedState` implémentent ce contrat et définissent la logique spécifique à chaque état.
3. Le `ReservationContext` englobe l'entité `Reservation` et permet de déléguer la gestion de l'état (les transitions) aux classes d'état.

### Avantages pour notre cas d'usage
1. **Lisibilité et Maintenabilité** : La logique métier de chaque état est isolée dans sa propre classe, respectant ainsi le principe de responsabilité unique (Single Responsibility Principle).
2. **Évolutivité** : Ajouter un nouvel état dans le futur (ex: `NO_SHOW`) ne nécessitera que la création d'une nouvelle classe implémentant `ReservationState`, sans modifier le code existant (respect du Open/Closed Principle).
3. **Sécurité des transitions** : Bien que simple dans cette implémentation basique, le pattern permet de sécuriser facilement les transitions (ex: empêcher de passer de `CANCELLED` à `COMPLETED`) directement dans les classes d'état.
