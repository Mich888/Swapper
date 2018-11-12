package swapper;

import java.util.*;
import java.util.concurrent.Semaphore;

public class Swapper<E> {
    private Map<E, Boolean> set; // mapa element: czy należy do swappera
    private Map<E, ArrayList<Pair<Semaphore, Integer>>> elementy;
    private Semaphore mutex = new Semaphore(1);

    private class Pair<S, T> {
        private S first;
        private T second;


        Pair(S first, T second) {
            this.first = first;
            this.second = second;
        }

        public S first() {
            return this.first;
        }
        public T second() {
            return this.second;
        }
        void setSecond(T second) {
            this.second = second;
        }
    }

    public Swapper() { // tworzy pusty HashSet
        this.set = new HashMap<>(); // zwykła mapa wystarczy
        this.elementy = new HashMap<>();
    }


    // chcemy mieć jakiś mechanizm który obudzi wątek w momencie gdy wszystkie elementy removed będą w secie
    public void swap(Collection<E> removed, Collection<E> added) {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Wątek przerwany"); // podnosimy mutex?
        }

        boolean isAllInMap = true;
        Pair<Semaphore, Integer> pomocniczy = new Pair(new Semaphore(1), 0); // TODO poprawić

        for (E element : removed) { // Sprawdzamy, czy wszystkie elementy z removed są w swapperze.
            if (!set.computeIfAbsent(element, (a) -> false)) { // Elementu nie ma w swapperze.
                isAllInMap = false;
                ArrayList<Pair<Semaphore, Integer>> lista = elementy.computeIfAbsent(element, (k) -> new ArrayList<>());
                lista.add(pomocniczy); // Dodajemy wątek do listy wątków oczekujących na dany element
                pomocniczy.setSecond(pomocniczy.second + 1); // Zwiększamy licznik brakujących elementów swapperze.
            }
        }

        if (!isAllInMap) { // usypiamy wątek
            try {
                pomocniczy.first().acquire();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Wątek przerwany"); // TODO podnosimy mutex?
            }
        }
        else { // usuwamy elementy z kolekcji removed ze swappera
            for (E element: removed) {
                set.put(element, false);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("main");
    }
}



