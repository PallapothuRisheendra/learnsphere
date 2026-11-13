INSERT INTO courses (slug, name, category, level, duration, description, icon, color, active, price_minor, currency) VALUES
('java-foundations', 'Java Foundations', 'Programming', 'Beginner', '6 weeks', 'Build a strong foundation in Java and object-oriented programming.', 'J', 'violet', TRUE, 0, 'INR'),
('python-for-everyone', 'Python for Everyone', 'Programming', 'Beginner', '5 weeks', 'Learn Python fundamentals and start building useful applications.', 'Py', 'blue', TRUE, 0, 'INR'),
('modern-web-development', 'Modern Web Development', 'Development', 'Intermediate', '8 weeks', 'Create beautiful, responsive websites using modern web technologies.', 'W', 'orange', TRUE, 0, 'INR'),
('data-structures-algorithms', 'Data Structures & Algorithms', 'Computer Science', 'Intermediate', '7 weeks', 'Understand essential data structures and efficient algorithms.', 'DS', 'green', TRUE, 0, 'INR'),
('database-design-sql', 'Database Design & SQL', 'Computer Science', 'Intermediate', '6 weeks', 'Explore databases, SQL queries, and relational database design.', 'DB', 'pink', TRUE, 0, 'INR');

INSERT INTO lessons (course_id, title, description, content, sort_order, active) VALUES
(1, 'Getting started with Java', 'Set up Java and learn how a program runs.', 'A Java application begins in a class with a main method.', 1, TRUE),
(1, 'Objects and classes', 'Model concepts with classes and objects.', 'Classes describe state and behavior.', 2, TRUE),
(1, 'Collections and error handling', 'Work with collections and expected failures.', 'Collections store groups of values.', 3, TRUE);
