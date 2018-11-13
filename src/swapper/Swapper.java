package swapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

public class Swapper<E> {
    private ConcurrentHashMap<E, Boolean> set; // mapa element: czy należy do swappera
    private ConcurrentHashMap<E, LinkedBlockingQueue<Pair<Semaphore, Integer>>> elementy;
    private Semaphore mutex = new Semaphore(1);

    private class Pair<Semaphore, Integer> {
        private Semaphore first;
        private int counter;


        Pair(Semaphore first, int second) {
            this.first = first;
            this.counter = second;
        }

        public Semaphore first() { return this.first; }
        public int second() { return this.counter; }

        void increaseCounter() { this.counter++; }
        void decreaseCounter() { this.counter--; }
    }

    public Swapper() { // Domyślny konstruktor - tworzy pusty swapper.
        this.set = new ConcurrentHashMap<>();
        this.elementy = new ConcurrentHashMap<>();
    }

    private void removeElements(Collection<E> removed) {
        for (E element: removed) {
            set.put(element, false); // Usuwamy element ze swappera. Klucze tych elementów na pewno są już w mapie.
            // Zwiększamy liczniki wątków, które chciały usunąć usunięte elementy.
            LinkedBlockingQueue<Pair<Semaphore, Integer>> lista = elementy.get(element); // Lista wątków, chcą usunąć dany element.
            for (Pair<Semaphore, Integer> thread: lista) {
                thread.increaseCounter(); // Zwiększamy licznik
            }
        }
    }

    private void addElements(Collection<E> added) {
        for (E element: added) { // Dodajemy do swappera elementy, których nie było wcześniej.
            if (!set.computeIfAbsent(element, (k) -> false)) { // Elementu nie było w swapperze.
                set.put(element, true);
                // Lista wątków, chcą usunąć dany element.
                LinkedBlockingQueue<Pair<Semaphore, Integer>> lista = elementy.computeIfAbsent(element, (k) -> new LinkedBlockingQueue<>());

                for (Pair<Semaphore, Integer> thread: lista) {
                    thread.decreaseCounter(); // Zmniejszamy licznik
                }
            }
        }
    }


    // chcemy mieć jakiś mechanizm który obudzi wątek w momencie gdy wszystkie elementy removed będą w secie
    public void swap(Collection<E> removed, Collection<E> added) throws InterruptedException {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Wątek przerwany"); // Podnosimy mutex?
        }

        boolean isAllInMap = true;
        Pair<Semaphore, Integer> pomocniczy = new Pair<Semaphore, Integer>(new Semaphore(1), 0);

        for (E element : removed) { // Sprawdzamy, czy wszystkie elementy z removed są w swapperze.
            if (!set.computeIfAbsent(element, (a) -> false)) { // Elementu nie ma w swapperze.
                isAllInMap = false;
            }

            // Lista wątków, które czekają na dany element.
            LinkedBlockingQueue<Pair<Semaphore, Integer>> lista = elementy.computeIfAbsent(element, (k) -> new LinkedBlockingQueue<>());

            if (!lista.contains(pomocniczy)) { // W kolekcjach elementy mogą się powtarzać.
                lista.add(pomocniczy); // Dodajemy wątek do listy wątków oczekujących na dany element
                pomocniczy.increaseCounter(); // Zwiększamy licznik brakujących elementów swapperze.
            }
        }

        if (!isAllInMap) { // Wątek zasypia na semaforze obiektu pomocniczego, który stworzył dla siebie.
            try {
                mutex.release(); // Zwalniamy mutexa - inny wątek może wejść do swappera.
                pomocniczy.first().acquire(); // Wątek zasypia na swoim semaforze.
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Wątek przerwany"); // TODO podnosimy mutex?
            }
        }
        else { // Usuwamy elementy z kolekcji removed ze swappera i dodajemy te z added, których nie było wcześniej.

            removeElements(removed);
            addElements(added);


            if (Thread.currentThread().isInterrupted()) { // Przywracamy pierwotny stan swappera.
                Thread.currentThread().interrupt();
                removeElements(added);
                addElements(removed);
                mutex.release();
            }

            // Sprawdzamy, czy możemy obudzić jakiś wątek.
            boolean isReady = false; //
            boolean isFound = false;
            Pair<Semaphore, Integer> deleted = new Pair<Semaphore, Integer>(new Semaphore(1), 1);

            for (LinkedBlockingQueue<Pair<Semaphore, Integer>> lista: elementy.values()) {
                for (Pair<Semaphore, Integer> thread: lista) {
                    if (thread.second() == 0) { // Budzimy ten wątek.
                        isReady = true;
                        if (!isFound) {
                            deleted = thread;
                            isFound = true;
                            lista.remove(deleted);
                        }
                        else {
                            if (thread.equals(deleted)) {
                                lista.remove(deleted);
                            }
                        }
                    }
                }
            }

            if (!isReady) { // Nie budzimy żadnego wątku.
                mutex.release();
            }
            else { // Budzimy wątek i nie podnosimy mutexa - obudzony wątek dziedziczy sekcję krytyczną.
                deleted.first().release();
            }
        }
    }
}



