/*TODO
*
** 
	Dato un file, diviso in:
	- 10 righe di intestazione
	- n righe di dati
	- 1 riga di EOF
	Modificare i dati presenti dopo il secondo spazio delle righe di dati con dei valori contenuti in un vettore.

	Quindi:
	1) Leggere il file partendo dalla 11 riga, o dalla prima ma leggendone 10 prima di iniziare a sovrascrivere i valori.
	2) Contare gli spazi presenti nella riga. Una volta arrivati a 2 spazi (o 1 se si inizia a contare da 0) inizio a riscrivere i valori.
	3) Passare alla riga successiva con il contatore degli spazi ri-inizializzato. 

	Metodo alternativo: al posto di inizializzare ogni volta il contatore, posso farlo partire da 1 e quando contatore%2==0 sovrascrivere.

	Punti critici:
	- Quando incontro un /n o /r (o qualsiasi cosa che vada a capo) devo fare in modo che mi lasci finire di scrivere se non ho ancora finito.
	- Se finisco di scrivere prima di aver cancellato tutto ciò che c'era scritto in precedenza, devo cancellare e spostare il segnale di fine riga.

	OSSERVAZIONI 
	- il file su cui fafre le modifiche qui si chiama "input.txt"
**
*
*/

#include <iostream>
#include <math.h>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <array>

using namespace std;

int main(argc, argv**)
{
	fstream file;
	string line;
	int count, conta_spazi;
	std::vector<float,8> sostituire;

	file = fopen("input.txt","w+");

	if(!file)
	{
		cout << "Error opening file" >> endl;
		return;
	}
	
	//Come scorrere le righe di un file 
	while(getline(file, line))
		count++;

	//Scorro le prime 10 righe
	//Dopo quelle inizio a controllare gli spazi

	for(/*le righe del file*/)
	{
		if(/*sono nelle prime 10 righe*/)
		{
			//Vado avanti
		}
		else
		{
			if(/*Il carattere in cui sono è uno spazio*/)
			{
				conta_spazi++;
				if(conta_spazi == 2)
				{
					/* Inizio a sostituire con i caratteri nel vettore "sostituire" */
					if(/*ho finito di scrivere*/)
					{
						if(/*Ci sono ancora cose dopo*/)
						{
							/*Cancello tutto ciò che viene fino a che non incontro un a capo*/
						}
						else
						{
							/*Metto la fine della riga e vado a capo*/
						}
					}
				}
			}
		}
	}

	fclose(&file);
}
