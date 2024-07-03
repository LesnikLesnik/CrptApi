# CrptApi

CrptApi - это Java-класс для работы с API Честного знака, который обеспечивает thread-safe выполнение запросов и поддерживает ограничение на количество запросов в определенный интервал времени.

## Описание

Этот проект реализует класс `CrptApi`, который позволяет отправлять запросы к API Честного знака для создания документов. Класс включает механизм ограничения количества запросов с использованием семафоров и планировщика задач. 
Реализованы внутренние классы для работы с HTTP-запросами и сериализацией JSON. 
